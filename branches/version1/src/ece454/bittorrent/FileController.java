package ece454.bittorrent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class FileController {

	private HashMap<String,FileChunks> fileChunks;
	
	public FileController() {
		fileChunks = new HashMap<String,FileChunks>();
		//TODO: look through localfiles and create a list of files the we have all the chunks for
		File localFiles = new File(Config.LOCAL_FOLDER_PATH);
		
		if(!localFiles.exists()) {
			localFiles.mkdir();
	    }
		
		String[] filesInLocalFolder = localFiles.list();
		for (String s : filesInLocalFolder) {
			fileChunks.put(s, new FileChunks(s, true));
		}
	}
	
	public String insertFile(String fullyQualifiedFilePath) throws IOException {
		
		File sourceFile = new File(fullyQualifiedFilePath);
		File destFile = null;
		String fileTitle = null; 
		
		if (!sourceFile.exists()) {
			UI.write("Error: File to insert does not exist");
			return null;
		}
		
		//get filename out of fully qualified url
		String[] fnPieces = fullyQualifiedFilePath.split("/");
		if (fnPieces.length > 0) {
			fileTitle = fnPieces[fnPieces.length-1];
			destFile = new File(Config.LOCAL_FOLDER_PATH+fileTitle);
		}
		
		if(!destFile.exists()) {
			destFile.createNewFile();
	    }

		//copy file to local folder
	    FileChannel source = null;
	    FileChannel destination = null;

	    try {
	        source = new FileInputStream(sourceFile).getChannel();
	        destination = new FileOutputStream(destFile).getChannel();
	        destination.transferFrom(source, 0, source.size());
	    } catch (FileNotFoundException e) {
			UI.write("Error: File to insert does not exist");
			fileTitle = null;
		} finally {
	        if(source != null) {
				source.close();
	        }
	        if(destination != null) {
	            destination.close();
	        }
	    }
		return fileTitle;
	}
	
	public int breakFileIntoChunks(String fileInLocalFolder) {

		File localFile = new File(Config.LOCAL_FOLDER_PATH+fileInLocalFolder);
		double numChunksExact = (double)localFile.length()/Config.CHUNK_SIZE_BYTES;
		int numberOfChunks = (int)Math.ceil((numChunksExact));
		ByteBuffer[] chunks = null;
		FileChannel localFileStream = null;
		//read file into chunks
		try {
			localFileStream = new FileInputStream(localFile).getChannel();
			//localFileStream.lock();
			chunks = new ByteBuffer[numberOfChunks];
			for (int x = 0; x < numberOfChunks-1; x++) {
				chunks[x] = ByteBuffer.allocate(Config.CHUNK_SIZE_BYTES);
			}
			
			int bytesLeft = (int) (localFile.length() % Config.CHUNK_SIZE_BYTES);
			if (bytesLeft != 0) {
				//last chunk will be a different size
				chunks[numberOfChunks-1] = ByteBuffer.allocate(bytesLeft);
			} else {
				chunks[numberOfChunks-1] = ByteBuffer.allocate(Config.CHUNK_SIZE_BYTES);
			}
			
			//byte[] ba = org.apache.commons.io.FileUtils.readFileToByteArray(localFile);
			
			double percentDone = 0;
			while (localFileStream.position() < localFile.length()) {
				double done = (double)localFileStream.position()/localFile.length();
				if (done > percentDone+0.10) {
					System.out.println("Breaking into chunks: " + Math.round(done*100) + "%");
					percentDone = done;
				}
				localFileStream.read(chunks);
			}
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (localFileStream != null) {
				try {
					localFileStream.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		FileChunks fc;
		if (fileChunks.get(fileInLocalFolder) == null) {
			fc = new FileChunks(fileInLocalFolder, chunks, true);
			fileChunks.put(fileInLocalFolder, fc);
		} else {
			fc = fileChunks.get(fileInLocalFolder);
			fc.setChunks(chunks, true);
		}
		
		return ReturnCodes.ERR_OK;
	}

	public HashMap<String, Object> neededChunks(String filename) {
		
		FileChunks fc = fileChunks.get(filename);
		
		HashMap<String, Object> chunksNeeded = new HashMap<String, Object>();
		
		if (fc == null) {
			//needs all the chunks for this file
			fc = new FileChunks(filename, false);
			fileChunks.put(filename, fc);
			chunksNeeded.put("needsChunks", 1);
		} else if (fc.hasAllChunks) {
			//doesnt need any chunks
			chunksNeeded.put("needsChunks", -1);
		} else {
			//needs some chunks, find out which ones
			chunksNeeded.put("needsChunks", 0);
			chunksNeeded.put("chunkList", fc.chunksNeeded());
		}
		return chunksNeeded;
	}

	public String getChunk(String filename, int chunkNum) {
		FileChunks fc = fileChunks.get(filename);
		if (fc.hasAllChunks && fc.getNumChunksInBuffer() == 0 ) {
			//need to read file into chunks first
			breakFileIntoChunks(filename);
		}
		//chunkIntString looks like 101+149+79+86...
		return fc.getChunk(chunkNum);
		
	}

//	public String getAllChunks(String filename, int offset) {
//		FileChunks fc = fileChunks.get(filename);
//		if (fc.hasAllChunks && fc.getNumChunksInBuffer() == 0 ) {
//			//need to read file into chunks first
//			breakFileIntoChunks(filename);
//		}
//		return fc.getAllChunks();
//	}

	public int getTotalNumberOfChunks(String filename) {
		FileChunks fc = fileChunks.get(filename);
		return fc.getTotalNumberOfChunks();
	}

	public void insertChunk(String filename, int chunkNum, int totalChunks, byte[] byteValues) {
		FileChunks fc = fileChunks.get(filename);
		//TODO if fc == null then the file needs to be created
		fc.addChunk(byteValues, chunkNum, totalChunks);
	}

	public String[][] getLocalFilesChunksStatus() {
		
		int numFiles = fileChunks.size();
		String[][] filesAndChunks = null;
		
		if (numFiles > 0) {
			filesAndChunks = new String[numFiles][2];
		
			int i = 0;
			for (String s : fileChunks.keySet()) {
				
				filesAndChunks[i][0] = s;
				
				FileChunks fc = fileChunks.get(s);
				if (fc.hasAllChunks) {
					filesAndChunks[i][1] = "all";
				} else {
					filesAndChunks[i][1] = fc.getChunksHaveList();
				}
				i++;
			}
		}
		return filesAndChunks;
	}

	public int totalChunksNumber(String filename) {
		FileChunks fc = fileChunks.get(filename);	
		return fc.getTotalNumberOfChunks();
	}

	public String compareChunkListsReturnNeeded(String filename, int[] chunkNumbers, int totalChunks) {
		FileChunks fc = fileChunks.get(filename);
		int[] chunksNeeded = null;
		if (fc == null) {
			//needs all the chunks for this file
			fc = new FileChunks(filename, false);
			fileChunks.put(filename, fc);
			return "all";
		} else if (fc.hasAllChunks) {
			//doesn't need any chunks
			return null;
		} else {
			//needs some chunks
			String chunksNeededString = "";
			chunksNeeded = fc.compareChunkList(chunkNumbers);
			
			boolean first = true;
			for (int i = 0; i < chunksNeeded.length; i++) {
				
				if (!first) {
					chunksNeededString += "+";
				}
				first = false;
				chunksNeededString += String.valueOf(chunksNeeded[i]);
			}
			return chunksNeededString;
		}
		
	}
}
