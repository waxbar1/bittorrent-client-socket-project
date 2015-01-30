package ece454.bittorrent;

public class Config {

	public static final int CHUNK_SIZE = 65536;
	public static final int CHUNK_SIZE_BYTES = 65536/8;
	public static final int MAX_PEERS = 6;

	// Cheesy, but allows us to do a simple Status class
	public static final int MAX_FILES = 100;
	public static String LOCAL_FOLDER_PATH;

}
