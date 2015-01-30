package ece454.bittorrent;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedList;

public class SocketServerHelper extends Thread {

	private boolean listening = true;
	private LinkedList<NodesServerThread> listenThreads;
	ServerSocket serverSocket = null;
	
	
	public SocketServerHelper(int bindPort) {
		super("SockServerHelper");
		listenThreads = new LinkedList<NodesServerThread>();
		
		try {
			serverSocket = new ServerSocket(bindPort);
		} catch (IOException e) {
			UI.write(e.getMessage());
		}
	}
	
	public int closeServerSocket() {
		
		Thread.currentThread().getThreadGroup().list();
		
		this.listening = false;
		
		for (NodesServerThread n : listenThreads) {
			n.leave();
		}
		
		try {
			if (serverSocket != null ) {
				serverSocket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return ReturnCodes.ERR_OK;
	}
	
	public void run() {
		while (listening) {
			try {
				Socket newSock = serverSocket.accept();
				NodesServerThread newNodeServerThread = new NodesServerThread(newSock);
				listenThreads.add(newNodeServerThread);
				newNodeServerThread.start();
			} catch (SocketException e) {
				UI.write("--->server sockets closed");
				//e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} 
		}
	}
	
}
