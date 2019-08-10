package appserver;

import java.util.HashMap;
import java.util.Map;

public class UserUpt {
	private Map<String, Boolean> userdevdataupt = new HashMap<>();
	private Map<String, Boolean> usergwdataupt  = new HashMap<>();
	
	public UserUpt() {
		// TODO Auto-generated constructor stub
	}
	
	public synchronized void UptUserDevices(String user, boolean upt) {
		userdevdataupt.put(user, upt);
	}
	
	public synchronized boolean CheckUserDevicesUpt(String user) {
		if (!userdevdataupt.containsKey(user))
			return false;
		
		return userdevdataupt.get(user);
	}
	
	public synchronized void UptUserGws(String user, boolean upt) {
		usergwdataupt.put(user, upt);
	}
	
	public synchronized boolean CheckUserGwsUpt(String user) {
		if (!usergwdataupt.containsKey(user))
			return false;
		
		return usergwdataupt.get(user);
	}
}
