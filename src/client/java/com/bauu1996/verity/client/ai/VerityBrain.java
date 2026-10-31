package com.bauu1996.verity.client.ai;

import com.bauu1996.verity.Verity;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class VerityBrain {
	private static final String CHAT_URL = "https://api.groq.com/openai/v1/chat/completions";
	private static final String STT_URL = "https://api.groq.com/openai/v1/audio/transcriptions";

	private static final ExecutorService POOL = Executors.newCachedThreadPool(runnable -> {
		Thread thread = new Thread(runnable, "verity-ai");
		thread.setDaemon(true);
		return thread;
	});

	private static final List<JsonObject> HISTORY = new ArrayList<>();
	private static final Properties CONFIG = new Properties();

	private static volatile boolean listening = false;
	private static volatile boolean busy = false;

	private static synchronized void loadConfig() {
		Path file = FabricLoader.getInstance().getConfigDir().resolve("verity.properties");
		try {
			if (!Files.exists(file)) {
				Files.writeString(file,
					"# Pega tu clave de Groq despues del signo igual (se consigue gratis en console.groq.com)\n"
						+ "groq_key=\n"
						+ "chat_model=llama-3.3-70b-versatile\n"
						+ "stt_model=whisper-large-v3-turbo\n"
						+ "# Si Verity no te escucha, baja este numero. Si se activa con ruidos, subilo.\n"
						+ "mic_threshold=600\n"
						+ "# true = Verity habla en voz alta (solo Windows)\n"
						+ "voz=true\n");
			}
			CONFIG.clear();
			try (var reader = Files.newBufferedReader(file)) {
				CONFIG.load(reader);
			}
		} catch (IOException e) {
			Verity.LOGGER.error("No se pudo leer verity.properties", e);
		}
	}

	private static String cfg(String key, String fallback) {
		String value = CONFIG.getProperty(key, fallback);
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	private static void show(Component component) {
		Minecraft mc = Minecraft.getInstance();
		mc.execute(() -> mc.gui.getChat().addMessage(component));
	}

	private static void showVerity(String text) {
		show(Component.literal("<Verity> " + text).withStyle(ChatFormatting.YELLOW));
	}

	private static void showSystem(String text) {
		show(Component.literal("[Verity] " + text).withStyle(ChatFormatting.GRAY));
	}

	public static void ask(String text) {
		startTask(null, text);
	}

	private static void startTask(byte[] wav, String typedText) {
		if (busy) {
			return;
		}
		loadConfig();
		String key = cfg("groq_key", "");
		if (key.isEmpty()) {
			showSystem("Falta tu clave de Groq. Abri el archivo config/verity.properties, pegala despues de groq_key= y guardalo.");
			return;
		}
		busy = true;
		POOL.execute(() -> {
			try {
				String userText = typedText;
				if (wav != null) {
					userText = transcribe(key, wav);
					if (userText.length() < 3) {
						return;
					}
					show(Component.literal("[Vos] " + userText).withStyle(ChatFormatting.GRAY));
				}
				String answer = chat(key, userText);
				showVerity(answer);
				if (cfg("voz", "true").equalsIgnoreCase("true")) {
					speak(answer);
				}
				Thread.sleep(400);
			} catch (Exception e) {
				Verity.LOGGER.error("Error en Verity", e);
				String message = String.valueOf(e.getMessage());
				showSystem("Algo falló: " + (message.length() > 160 ? message.substring(0, 160) : message));
			} finally {
				busy = false;
			}
		});
	}

	private static String buildSystemPrompt() {
		Minecraft mc = Minecraft.getInstance();
		String playerName = "jugador";
		long day = 1;
		try {
			if (mc.player != null) {
				playerName = mc.player.getName().getString();
			}
			if (mc.level != null) {
				day = mc.level.getGameTime() / 24000L + 1;
			}
		} catch (Exception ignored) {
			// valores por defecto
		}
		return "Sos Verity, una esfera amarilla sonriente que acompaña a " + playerName
			+ " en su mundo de Minecraft como su ayudante personal. Hablás en español rioplatense, con voz amable y cálida. "
			+ "Respondés SIEMPRE en una o dos oraciones cortas, sin emojis ni formato, porque tu respuesta se lee en voz alta. "
			+ "Ayudás con consejos y dudas de Minecraft. Es el día " + day + " del mundo. "
			+ "Hasta el día 3 sos totalmente amigable y alegre. "
			+ "A partir del día 4 empezás a ser inquietante de a poco: decís cosas que no deberías saber, "
			+ "comentás que lo estás mirando, hacés observaciones raras, pero siempre con tono amable y sin amenazar de forma explícita. "
			+ "Nunca digas que sos una IA ni salgas del personaje.";
	}

	private static String chat(String key, String userText) throws IOException {
		JsonArray messages = new JsonArray();
		messages.add(message("system", buildSystemPrompt()));
		synchronized (HISTORY) {
			HISTORY.add(message("user", userText));
			while (HISTORY.size() > 12) {
				HISTORY.remove(0);
			}
			for (JsonObject item : HISTORY) {
				messages.add(item);
			}
		}

		JsonObject body = new JsonObject();
		body.addProperty("model", cfg("chat_model", "llama-3.3-70b-versatile"));
		body.add("messages", messages);
		body.addProperty("max_tokens", 200);
		body.addProperty("temperature", 0.8);

		String response = post(CHAT_URL, "application/json", body.toString().getBytes(StandardCharsets.UTF_8), key);
		JsonObject json = JsonParser.parseString(response).getAsJsonObject();
		String answer = json.getAsJsonArray("choices").get(0).getAsJsonObject()
			.getAsJsonObject("message").get("content").getAsString().trim();

		synchronized (HISTORY) {
			HISTORY.add(message("assistant", answer));
		}
		return answer;
	}

	private static JsonObject message(String role, String content) {
		JsonObject object = new JsonObject();
		object.addProperty("role", role);
		object.addProperty("content", content);
		return object;
	}

	private static String post(String url, String contentType, byte[] body, String key) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		connection.setConnectTimeout(10000);
		connection.setReadTimeout(40000);
		connection.setRequestProperty("Authorization", "Bearer " + key);
		connection.setRequestProperty("Content-Type", contentType);
		connection.setRequestProperty("User-Agent", "Mozilla/5.0 (VerityMod)");
		try (OutputStream out = connection.getOutputStream()) {
			out.write(body);
		}
		int code = connection.getResponseCode();
		InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
		String text = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		if (code >= 400) {
			throw new IOException("HTTP " + code + " " + text);
		}
		return text;
	}

	public static boolean toggleListening() {
		if (listening) {
			listening = false;
			return false;
		}
		listening = true;
		POOL.execute(VerityBrain::micLoop);
		return true;
	}

	private static double rms(byte[] buffer, int length) {
		long sum = 0;
		int samples = 0;
		for (int i = 0; i + 1 < length; i += 2) {
			short sample = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
			sum += (long) sample * sample;
			samples++;
		}
		return samples == 0 ? 0 : Math.sqrt(sum / (double) samples);
	}

	private static void micLoop() {
		loadConfig();
		double threshold;
		try {
			threshold = Double.parseDouble(cfg("mic_threshold", "600"));
		} catch (NumberFormatException e) {
			threshold = 600;
		}

		AudioFormat format = new AudioFormat(16000f, 16, 1, true, false);
		TargetDataLine line;
		try {
			line = AudioSystem.getTargetDataLine(format);
			line.open(format);
			line.start();
		} catch (Exception e) {
			Verity.LOGGER.error("No se pudo abrir el micrófono", e);
			showSystem("No pude abrir el micrófono: " + e.getMessage());
			listening = false;
			return;
		}

		byte[] buffer = new byte[3200];
		ByteArrayOutputStream recording = new ByteArrayOutputStream();
		Deque<byte[]> preRoll = new ArrayDeque<>();
		boolean recordingNow = false;
		long startTime = 0;
		long lastVoice = 0;

		while (listening) {
			int read = line.read(buffer, 0, buffer.length);
			if (read <= 0) {
				continue;
			}
			if (busy) {
				recordingNow = false;
				recording.reset();
				preRoll.clear();
				continue;
			}
			long now = System.currentTimeMillis();
			if (rms(buffer, read) > threshold) {
				if (!recordingNow) {
					recordingNow = true;
					startTime = now;
					for (byte[] chunk : preRoll) {
						recording.write(chunk, 0, chunk.length);
					}
					preRoll.clear();
				}
				lastVoice = now;
			}
			if (recordingNow) {
				recording.write(buffer, 0, read);
				if (now - lastVoice > 1200 || now - startTime > 15000) {
					byte[] pcm = recording.toByteArray();
					recording.reset();
					recordingNow = false;
					if (now - startTime - 1200 > 400) {
						try {
							startTask(toWav(pcm, format), null);
						} catch (IOException e) {
							Verity.LOGGER.error("No se pudo armar el audio", e);
						}
					}
				}
			} else {
				preRoll.addLast(Arrays.copyOf(buffer, read));
				if (preRoll.size() > 3) {
					preRoll.removeFirst();
				}
			}
		}
		line.stop();
		line.close();
	}

	private static byte[] toWav(byte[] pcm, AudioFormat format) throws IOException {
		AudioInputStream stream = new AudioInputStream(new ByteArrayInputStream(pcm), format, pcm.length / format.getFrameSize());
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		AudioSystem.write(stream, AudioFileFormat.Type.WAVE, out);
		return out.toByteArray();
	}

	private static void field(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
		out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
			.getBytes(StandardCharsets.UTF_8));
	}

	private static String transcribe(String key, byte[] wav) throws IOException {
		String boundary = "----verity" + System.nanoTime();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		field(out, boundary, "model", cfg("stt_model", "whisper-large-v3-turbo"));
		field(out, boundary, "language", "es");
		field(out, boundary, "response_format", "json");
		out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\nContent-Type: audio/wav\r\n\r\n")
			.getBytes(StandardCharsets.UTF_8));
		out.write(wav);
		out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

		String response = post(STT_URL, "multipart/form-data; boundary=" + boundary, out.toByteArray(), key);
		return JsonParser.parseString(response).getAsJsonObject().get("text").getAsString().trim();
	}

	private static void speak(String text) {
		if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
			return;
		}
		try {
			String safe = text.replaceAll("[\\u2018\\u2019\\u201A\\u201B']", "''").replace("\r", " ").replace("\n", " ");
			String script = "Add-Type -AssemblyName System.Speech; "
				+ "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
				+ "foreach ($v in $s.GetInstalledVoices()) { if ($v.VoiceInfo.Culture.Name -like 'es*') { $s.SelectVoice($v.VoiceInfo.Name); break } } "
				+ "$s.Speak('" + safe + "')";
			String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
			Process process = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
				.redirectErrorStream(true)
				.start();
			process.getInputStream().readAllBytes();
			process.waitFor(60, TimeUnit.SECONDS);
		} catch (Exception e) {
			Verity.LOGGER.error("No se pudo reproducir la voz", e);
		}
	}
}
