package me.awildergoose.cachyflower;

import org.jetbrains.java.decompiler.api.Decompiler;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.util.Pair;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;

public class Main {
	static void main(String[] args) throws IOException, InterruptedException {
		if (args.length < 4) {
			System.out.println("Usage: threads cache_folder input.jar output.jar");
			System.exit(1);
		}

		String threads = args[0];
		String cache = args[1];
		String input = args[2];
		String output = args[3];

		ExecutorService executorService = Executors.newFixedThreadPool(Integer.parseInt(threads));

		File inputFile = new File(input);
		Path outputFile = Path.of(output);
		File indexFolder = new File(cache);
		boolean _ = indexFolder.mkdirs();

		Instant start = Instant.now();
		HashMap<String, Long> jarClasses = getJarClasses(inputFile);

		StringBuilder included = new StringBuilder("^(");
		CopyOnWriteArrayList<Pair<String, byte[]>> finalFiles = new CopyOnWriteArrayList<>();
		CopyOnWriteArrayList<String> includedArray = new CopyOnWriteArrayList<>();
		AtomicInteger hits = new AtomicInteger();
		AtomicInteger misses = new AtomicInteger();

		for (Map.Entry<String, Long> entry : jarClasses.entrySet()) {
			String path = entry.getKey();
			long hash = entry.getValue();
			Path indexFilePath = indexFolder.toPath().resolve(Long.toHexString(hash));

			executorService.submit(() -> {
				if (!Files.exists(indexFilePath)) {
					misses.addAndGet(1);

					includedArray.add(path + "|");
				} else {
					hits.addAndGet(1);

					try {
						finalFiles.add(Pair.of(
								path,
								Files.readAllBytes(indexFilePath)
						));
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			});
		}

		executorService.shutdown();
		if (!executorService.awaitTermination(100, TimeUnit.DAYS))
			System.out.println("why");

		included.append(String.join("", includedArray));

		if (included.toString().endsWith("|"))
			included = new StringBuilder(included.substring(0, included.length() - 1));

		included.append(")$");

		MemoryResultSaver out = new MemoryResultSaver();
		Decompiler decompiler = new Decompiler.Builder()
				.inputs(inputFile)
				.output(out)
				.option(IFernflowerPreferences.INCLUDED_CLASSES, included.toString())
				.option(IFernflowerPreferences.SKIP_EXTRA_FILES, true)
				.option(IFernflowerPreferences.THREADS, threads)
				.build();
		decompiler.decompile();

		out.finish.join();

		for (Map.Entry<String, String> entry : out.sources.entrySet()) {
			String path = entry.getKey();
			String source = entry.getValue();
			long hash = jarClasses.get(path);
			byte[] bytes = source.getBytes();

			Files.write(indexFolder.toPath().resolve(Long.toHexString(hash)), bytes);
			finalFiles.add(Pair.of(path, bytes));
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		try (JarOutputStream jar = new JarOutputStream(baos)) {
			for (Pair<String, byte[]> pair : finalFiles) {
				String path = pair.a;
				byte[] data = pair.b;

				jar.putNextEntry(new ZipEntry(path + ".java"));
				jar.write(data);
			}

			jar.finish();
		}

		Files.write(outputFile, baos.toByteArray());
		executorService.close();

		Duration t = Duration.between(start, Instant.now());
		System.out.printf(
				"Decompiled %d classes in %s with %d cache hits and %d cache misses!\n",
				hits.get() + misses.get(), // lol
				String.format("%dm %ds %dms",
				              t.toMinutesPart(),
				              t.toSecondsPart(),
				              t.toMillisPart()),
				hits.get(),
				misses.get()
		);
	}

	private static HashMap<String, Long> getJarClasses(File inputFile) throws IOException {
		CRC32 hasher = new CRC32();

		HashMap<String, Long> jarClasses = new HashMap<>();

		try (JarFile jarFile = new JarFile(inputFile)) {
			Enumeration<JarEntry> entries = jarFile.entries();

			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();

				byte[] classBytes;

				try (InputStream is = jarFile.getInputStream(entry)) {
					classBytes = is.readAllBytes();
				}

				String name = entry.getName();

				// go away
				if (name.startsWith("META-INF"))
					continue;
				// lambdas/subclasses GO AWAY!!!!!!!!
				if (name.contains("$"))
					continue;

				if (!entry.isDirectory() && name.endsWith(".class")) {
					hasher.update(classBytes);
					jarClasses.put(
							name.replace(".class", ""),
							hasher.getValue()
					);
				}
			}
		}

		return jarClasses;
	}
}
