package me.ilbba.mqtt.service;

public interface IAuthenticator {

	/**
	 * 校验用户名和密码是否正确
	 * @param username
	 * @param password
	 * @return boolean
	 */
	boolean checkValid(String username, String password);
	
}
