package ece454.bittorrent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;

public class DistributedFileTable {

	HashMap<String, Integer> filenameToNode;
	HashMap<String, String> openFiles;
	File openFilesStore;
	
	public DistributedFileTable() throws IOException {
		filenameToNode = new HashMap<String, Integer>();
		openFiles = new HashMap<String, String>();
		
		openFilesStore = new File(Config.LOCAL_FOLDER_PATH + Config.OPEN_FILES_FILENAME);
		if (openFilesStore.exists()) {
			BufferedReader br = new BufferedReader(new FileReader(openFilesStore));
		    try {
		        String line = br.readLine();
		        String[] filenameAndPermission;
		        
		        while (line != null) {
		        	filenameAndPermission = line.split(",");
		        	openFiles.put(filenameAndPermission[0], filenameAndPermission[1]);
		            line = br.readLine();
		        }
		    } finally {
		        br.close();
		    }
		}
	}
	
	public int addFileLocation(String filename, int nodeNum) {
		filenameToNode.put(filename, nodeNum);
		return ReturnCodes.ERR_OK;
	}
	
	public int setOpenFile(String filename, boolean for_writing) throws IOException {
		FileWriter writeOut = new FileWriter(openFilesStore, false);
		String filePermission;
		String fileInfo;
		String newPermission = (for_writing) ? "write" : "read";
		
		openFiles.put(filename, newPermission);
		for (String s : openFiles.keySet()) {
			filePermission = openFiles.get(s);
			fileInfo = s + "," + filePermission;
			writeOut.write(fileInfo);
		}
		writeOut.close();
		
		return ReturnCodes.ERR_OK;
	}
	
	public boolean closeFile(String filename) throws IOException {
		
		File closedFile = new File(Config.LOCAL_FOLDER_PATH + filename);
		String filePermission;
		String fileInfo;
		String deletePermission = null;
		
		if (openFiles.containsKey(filename)) {
			deletePermission = openFiles.get(filename);
		}
		
		if (deletePermission != null && deletePermission.equalsIgnoreCase("read")) {
			FileWriter writeOut = new FileWriter(openFilesStore, false);
			openFiles.remove(filename);
			for (String s : openFiles.keySet()) {
				filePermission = openFiles.get(s);
				fileInfo = s + "," + filePermission;
				writeOut.write(fileInfo);
			}
			writeOut.close();
			
			if (getFileLocation(filename) > 0) {
				closedFile.delete();
			}
			return true;
		}
		return false;
		
	}
	
	public int deleteUpdatedClosedFile(String filename) throws IOException {
		
		File closedFile = new File(Config.LOCAL_FOLDER_PATH + filename);
		String filePermission;
		String fileInfo;
		
		if (openFiles.containsKey(filename)) {
			FileWriter writeOut = new FileWriter(openFilesStore, false);
			openFiles.remove(filename);
			for (String s : openFiles.keySet()) {
				filePermission = openFiles.get(s);
				fileInfo = s + "," + filePermission;
				writeOut.write(fileInfo);
			}
			writeOut.close();
			
			if (getFileLocation(filename) > 0) {
				closedFile.delete();
			}
		}
		
		return ReturnCodes.ERR_OK;
	}
	
	public int getFileLocation(String filename) {
		if (filenameToNode.containsKey(filename)) {
			return filenameToNode.get(filename);
		} else {
			return -2;
		}
	}
	
	public String[] getAllFiles() {
		String[] files = new String[filenameToNode.size()];
		int count = 0;
		for (String s : filenameToNode.keySet()) {
			files[count] = s;
			count++;
		}
		return files;
	}
	
	public String[] getAllOpenFiles() {
		String[] files = new String[openFiles.size()];
		int count = 0;
		for (String s : openFiles.keySet()) {
			files[count] = s;
			count++;
		}
		return files;
	}
	
}
