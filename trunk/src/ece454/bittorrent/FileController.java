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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileController {

	private HashMap<String,FileChunks> fileChunks;
	private HashMap<String,FileChunks> updatedFileChunks;
	
	public FileController() {
		fileChunks = new HashMap<String,FileChunks>();
		updatedFileChunks = new HashMap<String,FileChunks>();
		
		//TODO: look through localfiles and create a list of files the we have all the chunks for
		File localFiles = new File(Config.LOCAL_FOLDER_PATH);
		
		if(!localFiles.exists()) {
			localFiles.mkdir();
	    }
		
		String[] filesInLocalFolder = localFiles.list();
		for (String s : filesInLocalFolder) {
			if (s.equalsIgnoreCase(Config.OPEN_FILES_FILENAME)) {
				continue;
			}
			
			if (!Peers.getInstance().fileTable.openFiles.containsKey(s)) { 
				fileChunks.put(s, new FileChunks(s, true));
				Peers.getInstance().fileTable.addFileLocation(s, -1);
			} else {
				updatedFileChunks.put(s, new FileChunks(s, true));
			}
		}
	}
	
	public String insertFile(String fullyQualifiedFilePath) throws IOException {
		return insertFile(fullyQualifiedFilePath, false);
	}
	
	public String insertFile(String filePath, boolean is_local) throws IOException {
		
		File sourceFile;
		if (is_local) {
			filePath = Config.LOCAL_FOLDER_PATH + filePath;
		}
		
		sourceFile = new File(filePath);

		File destFile = null;
		String fileTitle = null; 
		
		if (!sourceFile.exists()) {
			UI.write("Error: File to insert does not exist");
			return null;
		}
		
		//get filename out of fully qualified url
		String[] fnPieces = filePath.split("/");
		StringBuilder builder = new StringBuilder();
		String taggedFilename = null;
		String fileTitleNameExt = null;
		int tagValue = 0;
		
		if (fnPieces.length > 0) {
			fileTitle = fnPieces[fnPieces.length-1];
				Pattern p = Pattern.compile("_tag(\\d+)");
				Matcher m = p.matcher(fileTitle);

				if (m.find()) {
				    tagValue = Integer.parseInt(m.group(1)) + 1;
				    fileTitle = fileTitle.replaceAll("_tag(\\d+)","");
				}
			//}
			
			String[] fileTitlePieces = fileTitle.split("\\.");
			fileTitlePieces[fileTitlePieces.length-2] = fileTitlePieces[fileTitlePieces.length-2] + "_tag" + String.valueOf(tagValue);
			boolean first = true;
			for(String s : fileTitlePieces) {
			    if (!first) {
			    	builder.append(".");	
			    }
			    first = false;
				builder.append(s);
			}
			fileTitle = builder.toString();
			
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
		breakFileIntoChunks(fileInLocalFolder, fileChunks);
		return ReturnCodes.ERR_OK;
	}
	
	public int breakUpdatedFileIntoChunks(String fileInLocalFolder) {
		breakFileIntoChunks(fileInLocalFolder, updatedFileChunks);
		return ReturnCodes.ERR_OK;
	}
	
	public int breakFileIntoChunks(String fileInLocalFolder, HashMap<String,FileChunks> fileChunkStore) {

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
		if (fileChunkStore.get(fileInLocalFolder) == null) {
			fc = new FileChunks(fileInLocalFolder, chunks, true);
			fileChunkStore.put(fileInLocalFolder, fc);
		} else {
			fc = fileChunkStore.get(fileInLocalFolder);
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
	
	public String updatedGetChunk(String filename, int chunkNum) {
		FileChunks fc = updatedFileChunks.get(filename);
		if (fc.hasAllChunks && fc.getNumChunksInBuffer() == 0 ) {
			//need to read file into chunks first
			breakUpdatedFileIntoChunks(filename);
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

	public int getUpdatedTotalNumberOfChunks(String filename) {
		FileChunks fc = updatedFileChunks.get(filename);
		return fc.getTotalNumberOfChunks();
	}
	
	public int getTotalNumberOfChunks(String filename) {
		FileChunks fc = fileChunks.get(filename);
		return fc.getTotalNumberOfChunks();
	}

	public void insertChunk(String filename, int chunkNum, int totalChunks, byte[] byteValues) {
		insertChunk(filename, chunkNum, totalChunks, byteValues, fileChunks);
	}
	
	public void insertUpdatedChunk(String filename, int chunkNum, int totalChunks, byte[] byteValues) {
		insertChunk(filename, chunkNum, totalChunks, byteValues, updatedFileChunks);
	}
	
		
	public void insertChunk(String filename, int chunkNum, int totalChunks, byte[] byteValues, HashMap<String,FileChunks> fileChunkStore) {
		FileChunks fc = fileChunkStore.get(filename);
		if (fc == null) {
			fileChunkStore.put(filename, new FileChunks(filename, true));
			fc = fileChunkStore.get(filename);
		}
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

	public String fileNewTag(String filename) {
		String taggedFileTitle = null;
		try {
			taggedFileTitle = insertFile(filename, true);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return taggedFileTitle;
	}
}
