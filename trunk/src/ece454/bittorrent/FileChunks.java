package ece454.bittorrent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedList;

public class FileChunks {

	private String filename;
	//private int total_number_of_chunks;
	private ByteBuffer[] chunks = null;
	public boolean hasAllChunks = false;
	private int chunkCount;
	private int totalChunks;
	
	public FileChunks(String filename, boolean hasAllChunks) {
		this.filename = filename;
		this.hasAllChunks = hasAllChunks;
		this.chunkCount = 0;
	}
	
	public FileChunks(String filename, ByteBuffer[] b, boolean hasAllChunks) {
		this.chunks = b;
		this.filename = filename;
		this.hasAllChunks = hasAllChunks;
		
		this.totalChunks = b.length;
		if (hasAllChunks) {
			this.chunkCount = b.length;		
		} else {
			this.chunkCount = 0;
			for (ByteBuffer bb : b) {
				if (bb != null) {
					this.chunkCount++;
				}
			}
		}
	}
	
	public LinkedList<Integer> chunksNeeded() {
		
		LinkedList<Integer> l = new LinkedList<Integer>();
		
		for (int i = 0; i < chunks.length; i++) {
			if (chunks[i] == null) {
				l.add(i);
			}
		}
		return l;
	}
	
	public String getChunk(int i) {
		byte[] b = chunks[i].array();
		String chunkBytesAsString = "";
		boolean first = true;
		int n;
		for (int x = 0; x < b.length; x++) {
			if (!first) {
				chunkBytesAsString += "+";
			}
			n = b[x];
			chunkBytesAsString += String.valueOf(n);
			first = false;
		}
		
		return chunkBytesAsString;
	}

	public String[] getAllChunks() {
		String[] chunkStrings = new String[chunks.length];
		for (int x = 0; x < chunks.length; x++) {
			chunkStrings[x] = getChunk(x);
		}
		return chunkStrings;
		
	}

	public int getTotalNumberOfChunks() {
		if (chunks != null) {
			return chunks.length;
		}
		return 0;
	}

	public int addChunk(byte[] byteValues, int chunkNum, int totalChunks) {
		
		this.totalChunks = totalChunks;
		if (chunks == null) {
			chunks = new ByteBuffer[totalChunks];
			for (int i = 0; i < totalChunks; i++) {
				chunks[i] = null;
			}
		}
		
		ByteBuffer bb = ByteBuffer.wrap(byteValues);
		chunks[chunkNum] = bb;
		chunkCount++;
		
		if (chunkCount == totalChunks) {
			try {
				packChunksIntoFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return ReturnCodes.ERR_OK;
		
	}

	public int packChunksIntoFile() throws IOException {
		//write chunks to file
		File destFile = new File(Config.LOCAL_FOLDER_PATH+this.filename);
		if(!destFile.exists()) {
			destFile.createNewFile();
	    }
		
		FileChannel destination = null;
		try {
	        destination = new FileOutputStream(destFile).getChannel();
	        
	        double length = chunks[0].array().length*(chunks.length-1)+chunks[chunks.length-1].array().length;
	        while (destination.position() < length) {
	        	destination.write(chunks);
	        }
	    } catch (FileNotFoundException e) {
			e.printStackTrace();
		} finally {
	        if(destination != null) {
	            destination.close();
	        }
	    }
		return ReturnCodes.ERR_OK;
		
	}
	
	public int getNumChunksInBuffer() {
		return this.chunkCount;
	}
	
	public int getChunkBufferSize() {
		return this.totalChunks;
	}

	public String getChunksHaveList() {
		String chunkList = "";
		
		boolean first = true;
		for (int i = 0; i < chunks.length; i++) {
			if (chunks[i] != null) {
				
				if (!first) {
					chunkList += "+";
				}
				chunkList += String.valueOf(i);
				first = false;
			}
		}
		return chunkList;
	}

	public int[] compareChunkList(int[] chunkNumbers) {
		
		int[] chunksNeededFinal = null;
		LinkedList<Integer> chunksNeeded = new LinkedList<Integer>();
		
		for (int i = 0; i < chunkNumbers.length; i++) {
			if (chunks[i] == null) {
				chunksNeeded.add(i);
			}
		}
		
		if (chunksNeeded != null && chunksNeeded.size() > 0) {
			chunksNeededFinal = new int[chunksNeeded.size()];
			for (int x = 0; x < chunksNeeded.size(); x++) {
				chunksNeededFinal[x] = chunksNeeded.get(x);
			}
			return chunksNeededFinal;
		}
		return null;
	}

	public int setChunks(ByteBuffer[] newChunks, boolean hasAllChunks) {
		this.hasAllChunks = hasAllChunks;
		this.chunks = newChunks;
		this.chunkCount = chunks.length;
		return ReturnCodes.ERR_OK;
		
	}
	
}
