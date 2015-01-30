package ece454.bittorrent;

import java.util.Scanner;

public class Controller {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		
		int nodeNum = -1;
		
		try {
			nodeNum = Integer.parseInt(args[0]);
		} catch (NumberFormatException e) {
			UI.write("bad node number");
		}
		
		Config.LOCAL_FOLDER_PATH = "/home/ece351/workspace/ece454proj1/localFiles" + nodeNum + "/";
		
		
		
		Peers peers = Peers.getInstance();
		peers.initialize("/home/ece351/workspace/ece454proj1/peerFile.txt", nodeNum);
		
		Scanner sc = new Scanner(System.in);
		
		while (true) {
			String input = sc.nextLine();
			String[] words = input.split(" +");
			String action = words[0];
			
//			if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("q")) {
//				
//				//leave network
//				//cleanup
//				break;
//			}
			
			if (action.equalsIgnoreCase("query") && !words[1].isEmpty()) {
				int nodeNumber;
				
				try {
					nodeNumber = Integer.parseInt(words[1]);
					if (nodeNumber == nodeNum) {
						throw new NumberFormatException();
					}
					peers.net.queryPeerForFilesAndChunksItHas(nodeNumber);
				} catch (NumberFormatException e) {
					UI.write("Invalid Node Number");
				}
				
			} else if (action.equalsIgnoreCase("queryAll")) {
				peers.net.queryAllPeersForFilesAndChunksItHas();
			} else if (action.equalsIgnoreCase("leave")) {
				peers.net.leaveNetwork();
				break;
			} else if (action.equalsIgnoreCase("insert") && !words[1].isEmpty()) {
				//break file into chunks and copy it to a local folder
				peers.insertFile(words[1]);
			} else if (action.equalsIgnoreCase("join")) {
				peers.net.joinNetwork();
			}
		}
	}
	
	

}
