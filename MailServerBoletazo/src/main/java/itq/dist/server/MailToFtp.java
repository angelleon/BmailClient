package itq.dist.server;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MailToFtp
{
    File tmpDir = null;
    final static Logger logger = LogManager.getLogger(MailToFtp.class);
    private String fileName = "";

    private Date date = new Date();
    private LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    int month = localDate.getMonthValue();

    public MailToFtp(File tmpDir, String fileName)
    {
        this.tmpDir = tmpDir;
        this.fileName = fileName;
    }

    /**
     * genera una conexion con el servidor FTP y almacena la informacion del correo
     * enviado
     */
    public void ftpUpdate()
    {
        try
        {
            String nameMonth = intToMonth(month); // calcula la ruta del mes donde se guardara el archivo
            FTPClient ftpClient = new FTPClient();
            ftpClient.connect(InetAddress.getByName("172.16.8.240"));
            ftpClient.login("userboletazo", "123");

            int reply = ftpClient.getReplyCode(); // Verificar conexión con el servidor.
            // logger.debug("conexión FTP exitosa:" + reply);

            if (!FTPReply.isPositiveCompletion(reply))
            {
                logger.error("Error Code 6 - Problema al conectar con el servidor FTP");
            }
            ftpClient.changeWorkingDirectory("/" + nameMonth);// Cambiar directorio de trabajo
            // System.out.println("Se cambió satisfactoriamente el directorio");
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            BufferedInputStream buffIn = null;
            buffIn = new BufferedInputStream(new FileInputStream(tmpDir));// Ruta del archivo para enviar
            ftpClient.enterLocalPassiveMode();
            if (ftpClient.storeFile(fileName, buffIn))
                logger.info("FTP exitoso en ruta /" + nameMonth);
            else
                logger.info("FTP fallido a ruta /" + nameMonth);
            logger.debug("Operacion FTP realizada con exito");
            buffIn.close(); // Cerrar envio de arcivos al FTP
            ftpClient.logout(); // Cerrar sesión
            ftpClient.disconnect();// Desconectarse del servidor
            tmpDir.delete();
        }
        catch (Exception e)
        {
            logger.error(e.getMessage());
            logger.error("Error Code 6 - Problema al almacenar informacion en el servidor FTP");
        }
    }

    /**
     * traduce el mes en forma numerica a mes en forma de cadena de texto
     * 
     * @param numMonth
     * @return String month
     */
    private String intToMonth(int numMonth)
    {
        switch (numMonth)
        {
        case 1:
            return "enero";
        case 2:
            return "febrero";
        case 3:
            return "marzo";
        case 4:
            return "abril";
        case 5:
            return "mayo";
        case 6:
            return "junio";
        case 7:
            return "julio";
        case 8:
            return "agosto";
        case 9:
            return "septiembre";
        case 10:
            return "octubre";
        case 11:
            return "noviembre";
        case 12:
            return "diciembre";
        default:
            return "";
        }
    }
}
