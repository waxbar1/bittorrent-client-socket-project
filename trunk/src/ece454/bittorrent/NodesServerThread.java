package ece454.bittorrent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.HashMap;

/*
 * This class contains the sockets that listen for messages coming from the other nodes
 */

public class NodesServerThread extends Thread {

	private Socket socket = null;
	private PrintWriter out;
	private BufferedReader in;
	private boolean listening = false;
	
	public NodesServerThread(Socket socket) {
		super("NodesServerThread");
		this.socket = socket;
		this.listening = true;
	}
	
	public int leave() {
		try {
			this.listening = false;
			if (socket != null && socket.isConnected()) {
				socket.close();
			}
			if (out != null) {
				out.close();
			}
			if (in != null) {
				in.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return ReturnCodes.ERR_OK;
	}
	
	public void run() {
		
		try {
		    out = new PrintWriter(socket.getOutputStream(), true);
		    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		    String inputLine;
		    
		    while (this.listening && (inputLine = in.readLine()) != null) {   
		    	System.out.println(inputLine);
		    	HashMap<String, String> msg = Peers.getInstance().net.decodeNetMsg(inputLine);
		    	
		    	Peers peers = Peers.getInstance();
		    	
		    	if (msg.containsKey("command") && msg.get("command").equalsIgnoreCase("join")) {
		    		if (msg.containsKey("nodeNum")) {
		    			int nodeNum = Integer.parseInt(msg.get("nodeNum"));
		    			peers.net.joinAck(nodeNum);
		    		}
		    	} else if (msg.containsKey("command") && msg.get("command").equalsIgnoreCase("joinAck")) {
		    		if (msg.containsKey("nodeNum")) {
		    			int nodeNum = Integer.parseInt(msg.get("nodeNum"));
		    			peers.net.joinAckReceived(nodeNum);
		    		}
		    	} else if (msg.containsKey("command") && msg.get("command").equalsIgnoreCase("leave")) {
		    		if (msg.containsKey("nodeNum")) {
		    			int nodeNum = Integer.parseInt(msg.get("nodeNum"));
		    			peers.net.nodeLeaveHandler(nodeNum);
		    		}
		    	} else if (msg.containsKey("command") && msg.get("command").equalsIgnoreCase("insert")) {
		    		if (msg.containsKey("nodeNum") && msg.containsKey("filename")) {
		    			int nodeNum = Integer.parseInt(msg.get("nodeNum"));
		    			peers.net.insertedFileResponder(msg.get("filename"), nodeNum);
		    		}
		    	} else if (msg.containsKey("command") && msg.get("command").equalsIgnoreCase("needSomeChunks")) {
		    		if (msg.containsKey("nodeNum") && msg.containsKey("chunksNeeded") && msg.containsKey("filename")) {
		    			int nodeNum = Integer.parseInt(msg.get("nodeNum"));
		    			peers.net.sendChunksToNode(msg.get("filename"), msg.get("chunksNeeded"), nodeNum);
		    		}
		    	} else if (msg.containsKey("command") && msg.get("command").equalsIgnoreCase("needAllChunks")) {
		    		if (msg.containsKey("nodeNum") && msg.containsKey("filename")) {
		    			int nodeNum = Integer.parseInt(msg.get("nodeNum"));
		    			peers.net.sendAllChunksToNode(msg.get("filename"), nodeNum);
		    		}
		    	} else if (msg.containsKey("command") && msg.get("command").equalsIgnoreCase("needAllUpdatedChunks")) {
		    		if (msg.containsKey("nodeNum") && msg.containsKey("filename")) {
		    			int nodeNum = Integer.parseInt(msg.get("nodeNum"));
		    			peers.net.sendAllUpdatedChunksToNode(msg.get("filename"), nodeNum);
		    		}
		    	} else if (msg.containsKey("command") && msg.get("command").equalsIgnoreCase("sendingChunkData")) {
		    		if (msg.containsKey("nodeNum") && msg.containsKey("filename") && msg.containsKey("chunkData") && msg.containsKey("chunkNum") && msg.containsKey("totalChunks")) {
		    			int nodeNum = Integer.parseInt(msg.get("nodeNum"));
		    			int chunkNum = Integer.parseInt(msg.get("chunkNum"));
		    			int totalChunks = Integer.parseInt(msg.get("totalChunks"));
		    			peers.net.decodeChunk(msg.get("filename"), msg.get("chunkData"), chunkNum, totalChunks);
		    		}
		    	} else if (msg.containsKey("command") && msg.get("command").equalsIgnoreCase("sendingUpdatedChunkData")) {
		    		if (msg.containsKey("nodeNum") && msg.containsKey("filename") && msg.containsKey("chunkData") && msg.containsKey("chunkNum") && msg.containsKey("totalChunks")) {
		    			int nodeNum = Integer.parseInt(msg.get("nodeNum"));
		    			int chunkNum = Integer.parseInt(msg.get("chunkNum"));
		    			int totalChunks = Integer.parseInt(msg.get("totalChunks"));
		    			peers.net.decodeUpdatedChunk(msg.get("filename"), msg.get("chunkData"), chunkNum, totalChunks, nodeNum);
		    		}
		    	} else if (msg.containsKey("command") && msg.get("command").equalsIgnoreCase("queryForChunks")) {
		    		if (msg.containsKey("nodeNum")) {
		    			int nodeNum = Integer.parseInt(msg.get("nodeNum"));
		    			peers.net.sendLocalChunkListToPeer(nodeNum);
		    		}
		    	} else if (msg.containsKey("command") && msg.get("command").equalsIgnoreCase("fileAndChunkList")) {
		    		if (msg.containsKey("nodeNum") && msg.containsKey("filename") && msg.containsKey("chunkNums") && msg.containsKey("totalChunks")) {
		    			int nodeNum = Integer.parseInt(msg.get("nodeNum"));
		    			int totalChunks = Integer.parseInt(msg.get("totalChunks"));
		    			peers.net.compareLocalChunksToPeer(nodeNum, msg.get("filename"), msg.get("chunkNums"), totalChunks);
		    		}
		    		
		    	}  else if (msg.containsKey("command") && msg.get("command").equalsIgnoreCase("fileUpdated")) {
		    		if (msg.containsKey("nodeNum") && msg.containsKey("filename")) {
		    			int nodeNum = Integer.parseInt(msg.get("nodeNum"));
		    			//overwrite existing file
		    			peers.net.getUpdatedFile(msg.get("filename"), nodeNum);
		    		}
		    	} else if (msg.containsKey("command") && msg.get("command").equalsIgnoreCase("fileOverwritten")) {
		    		if (msg.containsKey("nodeNum") && msg.containsKey("filename")) {
		    			int nodeNum = Integer.parseInt(msg.get("nodeNum"));
		    			peers.fileTable.deleteUpdatedClosedFile(msg.get("filename"));
		    		}
		    	}  else if (msg.containsKey("command") && msg.get("command").equalsIgnoreCase("tagFile")) {
		    		if (msg.containsKey("nodeNum") && msg.containsKey("filename")) {
		    			peers.tagFile(msg.get("filename"));
		    		}
		    	}
		    }
		    
		} catch (IOException e) {
		    e.printStackTrace();
		}
		
	}
}
