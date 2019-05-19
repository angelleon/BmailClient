/**
 * 
 */
package itq.dist.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author Laptop
 *
 */
public class MailServer
{
    final static Logger logger = LogManager.getLogger(MailServer.class);
    final static int PORT = 2020;

    /**
     * @param args
     */
    public static void main(String[] args)
    {
        boolean alive = true;
        ServerSocket serverSocket;

        logger.info("MAIL SERVER INICIADO CON EXITO EN PUERTO [ " + PORT + "]");
        try
        {
            serverSocket = new ServerSocket(PORT);
            try
            {
                while (alive)
                {
                    Socket socket = serverSocket.accept();
                    logger.info("Procesando nueva solicitud...");
                    Thread mailThread = new MailThread(socket);
                    mailThread.start();
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
                logger.error("Ocurrio un error en el server.");
                logger.error(e.getMessage());
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
            logger.error("Ocurrio un error en el server.");
            logger.error(e.getMessage());
        }
    }

}
