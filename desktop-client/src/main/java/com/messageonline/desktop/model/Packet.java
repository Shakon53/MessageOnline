package com.messageonline.desktop.model;

/**
 * Константы типов сетевых пакетов.
 */
public final class Packet {
    public static final String REGISTER              = "REGISTER";
    public static final String LOGIN                 = "LOGIN";
    public static final String LOGOUT                = "LOGOUT";
    public static final String REGISTER_SUCCESS      = "REGISTER_SUCCESS";
    public static final String REGISTER_FAIL         = "REGISTER_FAIL";
    public static final String LOGIN_SUCCESS         = "LOGIN_SUCCESS";
    public static final String LOGIN_FAIL            = "LOGIN_FAIL";
    public static final String GLOBAL_MESSAGE        = "GLOBAL_MESSAGE";
    public static final String PRIVATE_MESSAGE       = "PRIVATE_MESSAGE";
    public static final String GET_HISTORY           = "GET_HISTORY";
    public static final String GET_PRIVATE_HISTORY   = "GET_PRIVATE_HISTORY";
    public static final String HISTORY_RESPONSE      = "HISTORY_RESPONSE";
    public static final String GET_USERS             = "GET_USERS";
    public static final String USER_LIST             = "USER_LIST";
    public static final String USER_JOINED           = "USER_JOINED";
    public static final String USER_LEFT             = "USER_LEFT";
    public static final String ERROR                 = "ERROR";
    public static final String NOTIFICATION          = "NOTIFICATION";

    private Packet() {}
}
