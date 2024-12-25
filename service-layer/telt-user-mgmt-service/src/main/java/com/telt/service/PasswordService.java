package com.telt.service;

public interface PasswordService {

    void forgotPassword(String emailOrMobile) throws Exception;

    void resetPassword(String token, String newPassword) throws Exception;

    void changePassword(String userId, String oldPassword, String newPassword) throws Exception;

}
