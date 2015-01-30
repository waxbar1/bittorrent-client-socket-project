package ece454.bittorrent;

public final class UI {
	
	public static int write(String[] output) {
		for (String s : output) {
			System.out.println(s);	
		}
		
		return 0;
	}
	
	public static int write(String output) {
		System.out.println(output);		
		return 0;
	}

}
