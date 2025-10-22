package mc.ardacraft.aclodgrabber.network;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;

public class NetworkManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("aclodgrabber.network");
    private static final String LODS_DOWNLOAD_URL = "https://lods.ardacraft.me/Distant_Horizons_server_data.zip";
    private static final int CONNECT_TIMEOUT = 4000;
    private static final int READ_TIMEOUT = 4000;
    private static final int DOWNLOAD_CONNECT_TIMEOUT = 30000;
    private static final int DOWNLOAD_READ_TIMEOUT = 30000;
    
    public static long getServerLastModified() throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(LODS_DOWNLOAD_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setInstanceFollowRedirects(true);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Server returned HTTP " + responseCode);
            }
            
            long lastModified = connection.getLastModified();
            LOGGER.debug("Server Last-Modified: {}", lastModified);
            return lastModified;
            
        } catch (UnknownHostException e) {
            LOGGER.error("Cannot resolve host: {}", LODS_DOWNLOAD_URL);
            throw new IOException("Cannot connect to server. Please check your internet connection.", e);
        } catch (SocketTimeoutException e) {
            LOGGER.error("Connection timeout to: {}", LODS_DOWNLOAD_URL);
            throw new IOException("Connection timeout. Please check your internet connection.", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    public static HttpURLConnection createDownloadConnection() throws IOException {
        URL url = new URL(LODS_DOWNLOAD_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(DOWNLOAD_CONNECT_TIMEOUT);
        connection.setReadTimeout(DOWNLOAD_READ_TIMEOUT);
        connection.setInstanceFollowRedirects(true);
        
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException("Server returned HTTP " + responseCode + " for download");
        }
        
        return connection;
    }
}