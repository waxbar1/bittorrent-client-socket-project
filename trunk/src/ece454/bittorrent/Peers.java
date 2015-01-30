package ece454.bittorrent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.activation.FileTypeMap;
import javax.swing.filechooser.FileSystemView;

/**
 * Peers is a dumb container to hold the peers; the number of peers is fixed,
 * but needs to be set up when a peer starts up; feel free to use some other
 * container class, but that class must have a method that allows it to read the
 * peersFile, since otherwise you have no way of having a calling entity tell
 * your code what the peers are in the system.
 **/
public class Peers {

	/**
	 * The peersFile is the name of a file that contains 
	 * a list of the peers. Its format is as follows: 
	 * in plaintext there are up to maxPeers lines, where
	 * each line is of the form: <IP address> <port number> 
	 * This file should be available on every machine 
	 * on which a peer is started, though you should
	 * exit gracefully if it is absent or incorrectly formatted. 
	 * After execution of this method, the peers should be present.
	 * 
	 * @param peersFile
	 * @return
	 */
	
	public NetworkOverlay net;
	public DistributedFileTable fileTable;
	private static Peers instance = null;
	private int nodeNumber;
	private FileController files;
	
	protected Peers() {};
	
	
	public static Peers getInstance() {
		if (instance == null) {
			instance = new Peers();
		}
		return instance;
	}
	
	public int initialize(String peersFile, int nodeNum) {
		numPeers = 0;
		this.nodeNumber = nodeNum;
		peers = new Peer[Config.MAX_PEERS];
		String peerInFile;
		String[] lineSegments;
		String port;
		int portNum;
		
		try {
			BufferedReader in = new BufferedReader(new FileReader(peersFile));
			int count = 0;
			while ( (peerInFile = in.readLine()) != null) {

				lineSegments = peerInFile.split(":"); 
				
				port = lineSegments[1];
				portNum = Integer.parseInt(port);

				if (count != nodeNumber) {
					addPeer(lineSegments[0], portNum);
				} else {
					//this is the info for the current peer (the port it's server should listen/bind to)
					addNullPeer();
					bindPort = portNum;
				}
				
				count++;
			}
			
			fileTable = new DistributedFileTable();
			
			net = new NetworkOverlay();
			net.connect();
			
		} catch (FileNotFoundException e) {
			UI.write(e.getMessage());
		} catch (IOException e) {
			UI.write(e.getMessage());
		}
		
		
		files = new FileController();
		
		return 0;
	}

	public int insertFile(String filename) {
		//copy the file to the local folder somehow manage chunks
			String insertedFile = null;
		
			try {
				insertedFile = files.insertFile(filename);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			if (insertedFile != null) {
				files.breakFileIntoChunks(insertedFile);
				net.queryPeersForNeededChunks(insertedFile);
			}
	    
		return ReturnCodes.ERR_OK;
	}
	
//	public int overwriteFileToUpdate() {
//		
//	}
	
	public int openFile(String filename, boolean for_writing) {
		int nodeNum = fileTable.getFileLocation(filename);
		

		//the file is remote
		//needs all chunks
		Peer p = peers[nodeNum];
		String[][] netMsg = {
			{"command", "needAllChunks"},
			{"nodeNum", String.valueOf(getNodeNum())},
			{"filename", filename}
		};
		if (p.isConnected() && thisNodeJoined) {
			p.writeToNodes(netMsg);
			try {
				fileTable.setOpenFile(filename, for_writing);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return ReturnCodes.ERR_OK;
	}
	
	public int closeFile(String filename) {
		
		boolean fileReadDeleted;
		int nodeNum;
		
		try {
			fileReadDeleted = fileTable.closeFile(filename);
			if (!fileReadDeleted) {
				nodeNum = fileTable.getFileLocation(filename);
				if (nodeNum > 0) {
					Peer p = peers[nodeNum];
					String[][] netMsg = {
						{"command", "fileUpdated"},
						{"nodeNum", String.valueOf(getNodeNum())},
						{"filename", filename}
					};
					if (p.isConnected() && thisNodeJoined) {
						p.writeToNodes(netMsg);
					}
				} else if (nodeNum == -1) {
					fileTable.deleteUpdatedClosedFile(filename);
				} else {
					System.out.println("File not found in network");
				}
				
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return ReturnCodes.ERR_OK;
	}
	
	public String getFileList() {
		String[] files = fileTable.getAllFiles();
		String fileList = "FILE LIST:\r\n";
		for (int x = 0; x < files.length; x++) {
			fileList += (x+1) + " - " + files[x] + "\r\n";
		}
		return fileList;
	}
	
	public String getOpenList() {
		String[] files = fileTable.getAllOpenFiles();
		String fileList = "FILE LIST:\r\n";
		for (int x = 0; x < files.length; x++) {
			fileList += (x+1) + " - " + files[x] + "\r\n";
		}
		return fileList;
	}
	
	
	public int getNodeNum() {
		return this.nodeNumber;
	}
	
	public Peer getPeer(int i) {
		return peers[i];
	}
	
	public Peer[] getPeers() {
		return peers;
	}
	
	public Peer getSelectedPeer() {
		return getPeer(selectedPeerNum-1);
	}
	
	public int selectPeer(int peerNum) {
		selectedPeerNum = peerNum;
		return ReturnCodes.ERR_OK;
	}
	
	private int addNullPeer() {
		if (numPeers < Config.MAX_PEERS) {
			peers[numPeers] = null;
			numPeers++;
			return ReturnCodes.ERR_OK;
		}
		return ReturnCodes.ERR_MAX_PEERS;
	}
	
	public int addPeer(String ipAddress, int portNum) {
		if (numPeers < Config.MAX_PEERS) {
			Peer p = new Peer(ipAddress, portNum);
			peers[numPeers] = p;
			numPeers++;
			return ReturnCodes.ERR_OK;
		}
		return ReturnCodes.ERR_MAX_PEERS;
		
	}

	//TODO You will likely want to add methods such as visit()

	private int numPeers;
	private Peer[] peers;
	private int selectedPeerNum;
	private int bindPort;
	private boolean thisNodeJoined = false;
	//private ServerSocket serverSocket = null;
	private SocketServerHelper sockServ = null;
	
	class NetworkOverlay {
		
		private int connect() {
			
			
			//set up server socket for listening for messages
			sockServ = new SocketServerHelper(bindPort);
			sockServ.start();
			
			
			//set up client sockets for sending messages
//			for (Peer p : peers) {
//				
//				if (p == null) continue;
//				
//				connectToClientSocket(p);
//				
//			}
			return 0;
		}
		
		public int connectToClientSocket(Peer p) {
			Socket s = null;
			InetAddress address;
			int port;
			
			s = p.getSocket();
			if (s == null) {
				s = new Socket();
			}
			
			if (!s.isConnected() && thisNodeJoined) {
				try {
					
					//connect to other nodes via socket
					address = InetAddress.getByName(p.getIp());
					port = p.getPort();
					InetSocketAddress socketAddress = new InetSocketAddress(address, port);
					s.connect(socketAddress);
					p.setSocket(s);
				} catch (UnknownHostException e) {
					UI.write(e.getMessage());
				} catch (IOException e) {
					UI.write(e.getMessage());
					//Peers.getInstance().net.nodeLeaveHandler(p);
				} 
			}
			return ReturnCodes.ERR_OK;
		}
		
		//tell all the nodes on the network that we are joining the network
		public int joinNetwork() {
		
			thisNodeJoined = true;
			for (Peer p : peers) {
				
				if (p == null) continue;
				connectToClientSocket(p);
				if (p.getSocket() != null && p.getSocket().isConnected()) {
					p.join();
					//sendLocalChunkListToPeer(p);
				}
			}
			return ReturnCodes.ERR_OK;
		}
		
		public int sendLocalChunkListToPeer(Peer p) {
			String[][] fileChunkList = files.getLocalFilesChunksStatus();
			
			if (fileChunkList != null) {
				for (String[] sx : fileChunkList) {
					String filename = sx[0];
					String chunksHave = sx[1];
					
					if (chunksHave.equalsIgnoreCase("all")) {
						queryPeersForNeededChunks(filename);
					} else {
						//sendChunksHaveToPeer(p, filename, chunksHave);
					}
				}
			}
			
			return ReturnCodes.ERR_OK;
		}

//		private int sendChunksHaveToPeer(Peer p, String filename, String chunksHave) {
//			String[][] netMsg = {
//					{"command", "fileAndChunkList"},
//					{"nodeNum", String.valueOf(getNodeNum())},
//					{"filename", filename},
//					{"chunksHave", chunksHave},
//					{"totalChunks", String.valueOf(files.totalChunksNumber(filename))}
//			};
//			if (p.isConnected() && thisNodeJoined) {
//				p.writeToNodes(netMsg);
//			}
//			return ReturnCodes.ERR_OK;
//			
//		}

		public int joinAck(int nodeNum) {
			if (thisNodeJoined) {
				Peer p = peers[nodeNum];
				if (p != null) {
					connectToClientSocket(p);
					p.setConnected();
					p.writeToNodes("joinAck", getNodeNum());
					//sendLocalChunkListToPeer(p);
				}
			}
			return ReturnCodes.ERR_OK;
		}
		
		public void joinAckReceived(int nodeNum) {
			Peer p = peers[nodeNum];
			p.setConnected();
			
			//sendLocalChunkListToPeer(p);
			p.query();
			net.sendLocalChunkListToPeer(nodeNum);
		}
		
//		public int sendListOfLocalFiles(int nodeNum) {
//			return ReturnCodes.ERR_OK;
//		}
		
		public int leaveNetwork() {

			for (Peer p : peers) {
				
				if (p == null) continue;
				
				if (p.isConnected()) {
					p.leave();
					
					try {
						if (p.getSocket() != null && p.getSocket().isConnected()) {
							p.getSocket().close();
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				
			}
			
			if (sockServ != null) {
				sockServ.closeServerSocket();
			}
			
			return ReturnCodes.ERR_OK;

		}
		
		public int nodeLeaveHandler(int nodeNum) {
			Peer p = peers[nodeNum];
			nodeLeaveHandler(p);
			return ReturnCodes.ERR_OK;
		}
		
		public int nodeLeaveHandler(Peer p) {
			if (p != null) {
				
				if (p.getSocket() != null && p.getSocket().isConnected()) {
					try {
						p.getSocket().close();
						p.setSocket(null);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				p.setDisconnected();
				//deal with active file transfers with this node and stuff like that
			}
			return ReturnCodes.ERR_OK;
		}
		
		public int queryPeersForNeededChunks(String filename) {
			for (Peer p : peers) {
				if (p != null) {
					if (p.isConnected() && thisNodeJoined) {
						p.insert(filename);
					}
				}
			}
			return ReturnCodes.ERR_OK;
		}
		
		public String encodeNetMsg(String[][] msgToEncode) {
			String retMsg = "";
			boolean first = true;
			for (String[] objs : msgToEncode) {
				if (!first) {
					retMsg += ",";
				}
				first = false;
				retMsg += objs[0] + ":" + objs[1];
				
			}
			return retMsg;
		}
	
		public HashMap<String,String> decodeNetMsg(String msg) {
			String[] objs = msg.split(",");
			
			HashMap<String, String> retMsg = new HashMap<String, String>(objs.length);
			for (int i = 0; i < objs.length; i++) {
				String[] params = objs[i].split(":");
				retMsg.put(params[0], params[1]);
			}
			return retMsg;
		}

		public void insertedFileResponder(String filename, int nodeNum) {
			fileTable.addFileLocation(filename, nodeNum);
		}

		public int sendChunksToNode(String filename, String chunkList, int nodeNum) {
			//chunkList looks like for example 0+1+2+3+4+6
			String[] chunkNumStrings = chunkList.split("\\+");
			String chunkString = null;
			int chunkNum = -1;
			
			int totalChunks = files.getTotalNumberOfChunks(filename);
			for (String s : chunkNumStrings) {
				chunkNum = Integer.parseInt(s);
				chunkString = files.getChunk(filename, chunkNum);
			}
			sendChunkToNode(chunkString, filename, chunkNum, totalChunks, nodeNum);
			return ReturnCodes.ERR_OK;
		}

		public int sendAllChunksToNode(String filename, Peer p) {
			files.breakFileIntoChunks(filename);
			int numOfChunks = files.getTotalNumberOfChunks(filename);
			String chunkData;
			for (int i = 0; i < numOfChunks; i++) {
				chunkData = files.getChunk(filename, i);
				if (!chunkData.isEmpty()) {
					sendChunkToNode(chunkData, filename, i, numOfChunks, p);
				}
			}
			return ReturnCodes.ERR_OK;
		}
		
		public int sendAllChunksToNode(String filename, int nodeNum) {
			files.breakFileIntoChunks(filename);
			int numOfChunks = files.getTotalNumberOfChunks(filename);
			String chunkData;
			for (int i = 0; i < numOfChunks; i++) {
				chunkData = files.getChunk(filename, i);
				if (!chunkData.isEmpty()) {
					sendChunkToNode(chunkData, filename, i, numOfChunks, nodeNum);
				}
			}
			return ReturnCodes.ERR_OK;
			
		}
		
		public void sendAllUpdatedChunksToNode(String filename, int nodeNum) {
			files.breakUpdatedFileIntoChunks(filename);
			int numOfChunks = files.getUpdatedTotalNumberOfChunks(filename);
			String chunkData;
			for (int i = 0; i < numOfChunks; i++) {
				chunkData = files.updatedGetChunk(filename, i);
				if (!chunkData.isEmpty()) {
					sendUpdatedChunkToNode(chunkData, filename, i, numOfChunks, nodeNum);
				}
			}
		}
		
		private int sendChunkToNode(String chunkString, String filename, int chunkNum, int totalChunks, int nodeNum) {
			Peer p = peers[nodeNum];
			return sendChunkToNode(chunkString, filename, chunkNum, totalChunks, p);
		}
		
		private int sendChunkToNode(String chunkString, String filename, int chunkNum, int totalChunks, Peer p) {
			
			String[][] netMsg = {
				{"command", "sendingChunkData"},
				{"nodeNum", String.valueOf(getNodeNum())},
				{"filename", filename},
				{"chunkNum", String.valueOf(chunkNum)},
				{"totalChunks", String.valueOf(totalChunks)},
				{"chunkData", chunkString}
			};
			if (p.isConnected() && thisNodeJoined) {
				p.writeToNodes(netMsg);
			}
			return ReturnCodes.ERR_OK;
		}
		
		private int sendUpdatedChunkToNode(String chunkString, String filename, int chunkNum, int totalChunks, int nodeNum) {
			Peer p = peers[nodeNum];
			String[][] netMsg = {
				{"command", "sendingUpdatedChunkData"},
				{"nodeNum", String.valueOf(getNodeNum())},
				{"filename", filename},
				{"chunkNum", String.valueOf(chunkNum)},
				{"totalChunks", String.valueOf(totalChunks)},
				{"chunkData", chunkString}
			};
			if (p.isConnected() && thisNodeJoined) {
				p.writeToNodes(netMsg);
			}
			return ReturnCodes.ERR_OK;
		}

		
		public int decodeChunk(String filename, String chunkData, int chunkNum, int totalChunks) {
			byte[] byteValues = doDecodeChunk(filename, chunkData, chunkNum, totalChunks);
			files.insertChunk(filename, chunkNum, totalChunks, byteValues);
			return ReturnCodes.ERR_OK;
		}
		
		public int decodeUpdatedChunk(String filename, String chunkData, int chunkNum, int totalChunks, int nodeNum) {
			byte[] byteValues = doDecodeChunk(filename, chunkData, chunkNum, totalChunks);
			files.insertUpdatedChunk(filename, chunkNum, totalChunks, byteValues);
			if (chunkNum == (totalChunks -1)) {
				notifyFileOverwritten(filename, nodeNum);
			}
			return ReturnCodes.ERR_OK;
		}
		
		public void notifyFileOverwritten(String filename, int nodeNum) {
			Peer p = peers[nodeNum];
			String[][] netMsg = {
					{"command", "fileOverwritten"},
					{"nodeNum", String.valueOf(getNodeNum())},
					{"filename", filename}
				};
				if (p.isConnected() && thisNodeJoined) {
					p.writeToNodes(netMsg);
				}
		}
		
		public byte[] doDecodeChunk(String filename, String chunkData, int chunkNum, int totalChunks) {
			String[] chunkNumStrings = chunkData.split("\\+");
			byte[] byteValues = null;
			byte[] byteValues1 = new byte[chunkNumStrings.length];
			int nullCount = 0;
			
			for (int i = 0; i < chunkNumStrings.length; i++) {
				int sToi = Integer.parseInt(chunkNumStrings[i]);
				if (chunkNum == (totalChunks-1) && sToi == 0) {
					nullCount++;
					continue;
				}

				String iToBi = (String)Integer.toBinaryString(sToi);
				if (iToBi.length() > 8) {
					iToBi = iToBi.substring(iToBi.length()-8, iToBi.length());
				}

				byte biToBy = (byte) Integer.parseInt(iToBi, 2);
				byteValues1[i] = biToBy;
			}
			
			byteValues = byteValues1;
			
			return byteValues;
		}

		public int queryPeerForFilesAndChunksItHas(int nodeNumber) {
			Peer p = peers[nodeNumber];
			if (p.isConnected() && thisNodeJoined) {
				p.query();
			}
			return ReturnCodes.ERR_OK;
			
		}

		public synchronized int sendLocalChunkListToPeer(int nodeNum) {
			Peer p = peers[nodeNum];
			sendLocalChunkListToPeer(p);
			return ReturnCodes.ERR_OK;
			
		}

		public int compareLocalChunksToPeer(int nodeNum, String filename, String chunkNums, int totalChunks) {
			Peer p = peers[nodeNum];
			String[] chunkNumsStrings = chunkNums.split("\\+");
			int[] chunkNumbers = new int[chunkNumsStrings.length];
			String chunksNeeded;
			
			for (int i = 0; i < chunkNumsStrings.length; i++) {
				chunkNumbers[i] = Integer.parseInt(chunkNumsStrings[i]);
			}
			chunksNeeded = files.compareChunkListsReturnNeeded(filename, chunkNumbers, totalChunks);
			
			if (chunksNeeded != null) {
				if (chunksNeeded.equalsIgnoreCase("all")) {
					//needs all chunks
					String[][] netMsg = {
						{"command", "needAllChunks"},
						{"nodeNum", String.valueOf(getNodeNum())},
						{"filename", filename}
					};
					if (p.isConnected() && thisNodeJoined) {
						p.writeToNodes(netMsg);
					}
				} else {	
					//needs some chunks
					String[][] netMsg = {
						{"command", "needSomeChunks"},
						{"nodeNum", String.valueOf(getNodeNum())},
						{"filename", filename},
						{"chunksNeeded", chunksNeeded}
					};
					if (p.isConnected() && thisNodeJoined) {
						p.writeToNodes(netMsg);
					}
				}
			}
			
			return ReturnCodes.ERR_OK;	
		}

		public int queryAllPeersForFilesAndChunksItHas() {
			for (Peer p : peers) {
				if (p != null) {
					if (p.isConnected() && thisNodeJoined) {
						p.query();
					}
				}
			}
			return ReturnCodes.ERR_OK;
		}

		public void getUpdatedFile(String filename, int nodeNum) {
			Peer p = peers[nodeNum];
			String[][] netMsg = {
					{"command", "needAllUpdatedChunks"},
					{"nodeNum", String.valueOf(getNodeNum())},
					{"filename", filename}
				};
				if (p.isConnected() && thisNodeJoined) {
					p.writeToNodes(netMsg);
				}
		}

		

		
		
		

	}

	public void tagFile(String filename) {
		int nodeNum = fileTable.getFileLocation(filename);
		if (nodeNum > 0) {
			//send tag message to node
			Peer p = peers[nodeNum];
			String[][] netMsg = {
				{"command", "tagFile"},
				{"nodeNum", String.valueOf(getNodeNum())},
				{"filename", filename}
			};
			if (p.isConnected() && thisNodeJoined) {
				p.writeToNodes(netMsg);
			}
		} else if (nodeNum == -1) {
			//copy file and increase tag
			String newTaggedFile = files.fileNewTag(filename);
			fileTable.addFileLocation(newTaggedFile, -1);
			net.queryPeersForNeededChunks(newTaggedFile);
		}
	}

	
	public int activePeerCount() {
		int peerCount = 0;
		for (Peer p : peers) {
			if (p != null) {
				if (p.isConnected()) {
					peerCount++;
				}
			}
		}
		return peerCount;
	}

	public void retireThisNode() {
		
		File localFiles = new File(Config.LOCAL_FOLDER_PATH);
		String[] filesInLocalFolder = localFiles.list();
		int filesPerPeer = (int)Math.ceil((double)filesInLocalFolder.length/activePeerCount());
		int i = 0;
		int count = filesPerPeer;
		
		for (Peer p : peers) {
			if (p != null) {
				if (p.isConnected() && thisNodeJoined) {
					while (count > 0) {
						net.sendAllChunksToNode(filesInLocalFolder[i], p);
						i++;
						count--;
						
						if (i == filesInLocalFolder.length) {
							break;
						}
					}
					count = filesPerPeer;
				}
			}
		}
	}

}
