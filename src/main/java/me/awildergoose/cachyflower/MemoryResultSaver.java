package me.awildergoose.cachyflower;

import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.jar.Manifest;

public class MemoryResultSaver implements IResultSaver {
	public HashMap<String, String> sources = new HashMap<>();
	public CompletableFuture<Void> finish = new CompletableFuture<>();

	public MemoryResultSaver() {}

	@Override
	public void saveFolder(String path) {
		// no-op
	}

	@Override
	public void copyFile(String source, String path, String entryName) {
		// no-op, we don't want to copy resources
	}

	@Override
	public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
		// no-op, this should never happen?
	}

	@Override
	public void createArchive(String path, String archiveName, Manifest manifest) {
		// no-op
	}

	@Override
	public void saveDirEntry(String path, String archiveName, String entryName) {
		// no-op
	}

	@Override
	public void copyEntry(String source, String path, String archiveName, String entry) {
		// no-op
	}

	@Override
	public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
		this.sources.put(entryName.replace(".java", ""), content);
	}

	@Override
	public void closeArchive(String path, String archiveName) {
		this.finish.complete(null);
	}
}
