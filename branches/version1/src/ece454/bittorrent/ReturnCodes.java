package ece454.bittorrent;

public class ReturnCodes {

	// Everything good
	public final static int ERR_OK = 0;
	// Unknown warning
	public final static int ERR_UNKNOWN_WARNING = 1;
	// Unknown error
	public final static int ERR_UNKNOWN_FATAL = -2;
	// Cannot connect to anything; fatal error
	public final static int ERR_CANNOT_CONNECT = -3;
	// Cannot find any peer (e.g., no peers in a peer file); fatal
	public final static int ERR_NO_PEERS_FOUND = -4;
	// Cannot find some peer; warning, since others may be connectable
	public final static int ERR_PEER_NOT_FOUND = 5;

	// TODO please add as necessary
	public final static int ERR_MAX_PEERS = -6;
	public final static int ERR_NODE_INACTIVE = -7;
	public final static int MSG_PACK_FILE = -8;
}
