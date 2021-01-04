package com.example.journal;

import java.io.DataOutputStream;
import java.io.FileWriter;
import java.net.*;
import java.util.List;

public class LogInManager {
    // constants
    private String USER_AGENT = "Mozilla/5.0";

    // login data
    private String sessionid = "";
    private String csrftoken = "";

    // assistant variables
    private CookieManager cookieManager = new CookieManager();
    // URL data
    private String pupilUrl;
    private String postParameters;

    private String ROOT_DIRECTORY;

    // constructor
    public LogInManager(String rtDir) {
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);

        ROOT_DIRECTORY = new String(rtDir);
    }

    private void setPostParameters(String un, String pw) {
        postParameters = new String("");
        postParameters += "csrfmiddlewaretoken=";
        postParameters += csrftoken;
        postParameters += "&username=";
        postParameters += un;
        postParameters += "&password=";
        postParameters += pw;
    }

    public void writeLoginDataToFiles(String username) {
        try {
            FileWriter foutUsername = new FileWriter(ROOT_DIRECTORY + "/UserData/username.txt", false);
            foutUsername.write(username);
            foutUsername.flush();
            foutUsername.close();
        } catch (Exception e) {
            System.out.println("foutUsername error");
        }

        try {
            FileWriter foutSessionid = new FileWriter(ROOT_DIRECTORY + "/UserData/sessionid.txt", false);
            foutSessionid.write(sessionid);
//            System.out.println(sessionid);
            foutSessionid.flush();
            foutSessionid.close();
        } catch (Exception e) {
            System.out.println("foutSessionid error");
        }

        try {
            FileWriter foutUrl = new FileWriter(ROOT_DIRECTORY + "/UserData/pupilUrl.txt", false);
            foutUrl.write(pupilUrl);
            foutUrl.flush();
            foutUrl.close();
        } catch (Exception e) {
            System.out.println("foutUrl error");
        }

        try {
            FileWriter foutStatus = new FileWriter(ROOT_DIRECTORY + "/UserData/status.txt", false);
            foutStatus.write("YES");
            foutStatus.flush();
            foutStatus.close();
        } catch (Exception e) {
            System.out.println("foutStatus error");
        }
    }

    public void takeCsrftoken() throws Exception {
        // open connection
        URL connectionUrl = new URL("https://schools.by/login");
        HttpURLConnection con = (HttpURLConnection)connectionUrl.openConnection();

        // set connection args
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", USER_AGENT);

        // get cookies
        con.getContent();
        CookieStore cookieJar = cookieManager.getCookieStore();
        List<HttpCookie> cookies = cookieJar.getCookies();

        // find csrftoken in cookies and set it
        for (HttpCookie cookie : cookies) {
            if (cookie.getName().equals("csrftoken")) {
                csrftoken = cookie.getValue();
                break;
            }
        }
    }

    public LoginState loggedIn(String username, String password) {
        try {
            takeCsrftoken();
        } catch (Exception e1) {
            try {
                takeCsrftoken();
            } catch (Exception e2) {
                return LoginState.ERROR_OCCURED;
            }
        }

        setPostParameters(username, password);

        URL connectionUrl;
        HttpURLConnection con;
        try {
            // open connection
            connectionUrl = new URL("https://schools.by/login");
            con = (HttpURLConnection) connectionUrl.openConnection();

            // configure connection
            con.setInstanceFollowRedirects(false);
            con.setUseCaches(false);

            con.setRequestMethod("POST");
            con.setRequestProperty("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            con.setRequestProperty("accept-encoding", "gzip, deflate, br");
            con.setRequestProperty("accept-language", "en-US,en;q=0.9");
            con.setRequestProperty("content-length", Integer.toString(postParameters.length()));
            con.setRequestProperty("content-type", "application/x-www-form-urlencoded");
            con.setRequestProperty("cookie", "csrftoken=" + csrftoken + ";"); //cookie
            con.setRequestProperty("origin", "https://schools.by");
            con.setRequestProperty("referer", "https://schools.by/login");
            con.setRequestProperty("user-agent", USER_AGENT);

            con.setDoOutput(true);
            con.setDoInput(true);
        } catch (Exception e) {
            return LoginState.ERROR_OCCURED;
        }

        // send data
        try {
            DataOutputStream stream = new DataOutputStream(con.getOutputStream());
            stream.writeBytes(postParameters);
            stream.flush();
            stream.close();
        } catch (Exception e) {
            return LoginState.ERROR_OCCURED;
        }

        try {
            con.getContent();
        } catch (Exception e) {
            return LoginState.ERROR_OCCURED;
        }


        // login check
        int responseCode = -1;
        try {
            responseCode = con.getResponseCode();
        } catch (Exception e) {
            return LoginState.ERROR_OCCURED;
        }
        while (responseCode == -1) {}
        System.out.println("responseCode -> " + responseCode);
        if (responseCode == 200) {
            return LoginState.WRONG_PASSWORD;
        }

        // get sessionid and new csrftoken
        CookieStore cookieJar = cookieManager.getCookieStore();
        List <HttpCookie> cookies = cookieJar.getCookies();
        for (HttpCookie cookie : cookies) {
            if (cookie.getName().equals("csrftoken")) {
                csrftoken = cookie.getValue();
            }
            if (cookie.getName().equals("sessionid")) {
                sessionid = cookie.getValue();
            }
        }

        pupilUrl = con.getHeaderField("location");
        pupilUrl += "/dnevnik/";

        return LoginState.LOGGED_IN;
    }

    public String getCsrftoken() {
        return csrftoken;
    }

    public String getSessionid() {
        return sessionid;
    }

    public String getPupilUrl() {
        return pupilUrl;
    }

}
