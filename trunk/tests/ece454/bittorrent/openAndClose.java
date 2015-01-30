package ece454.bittorrent;
import static org.junit.Assert.*;

import org.junit.Test;


public class openAndClose {
	
	Controller tester = new Controller();
	Peers peers = Peers.getInstance();
	
	@Test
	public void test() {
		Controller.main(new String[]{"0"});
		peers.net.joinNetwork();
	}

}
