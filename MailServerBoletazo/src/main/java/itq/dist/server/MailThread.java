package itq.dist.server;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;

public class MailThread extends Thread
{
    final static Logger logger = LogManager.getLogger(MailThread.class);
    // Datos de la cuenta de correo personalizada de boletazo.
    private static final String host = "smtp.gmail.com";
    private static final String user = "boletazo.mail@gmail.com";
    private static final String pass = "boletazodev12345!";
    private static final int port = 465;
    // Datos de operacion
    private final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    private final String IMG_SELLO_PATH = "D:\\opt\\PDFBoletazo\\selloValidez.png";
    private final String PDF_BASEPATH = "D:\\opt\\PDFBoletazo\\BoletazoCompra.pdf";
    private final int MSG_INTEGRITY = 6; // Numero de tramas en el mensaje

    private static enum errorCode {
        exito,
        msg_integrity,
        msg_badFormat,
        doc_error,
        null_account,
        generic,
    }

    private errorCode eCode = errorCode.exito; // exito por default
    // Datos del correo.
    private String msgAsunto = "BOLETAZO - ¡CONFIRMACION DE COMPRA!";
    private String msgCuerpo = "Gracias por comprar usando Boletazo, tu boleto " +
            "se encuentra dentro del PDF adjunto";
    private String destinatario = "";
    private String eventID = "Generic Event!";
    private String userID = "00000";
    private String seatID = "##";
    private String dateEvent = "dd/mm/yyyy";
    private String fileName = "";
    private Socket socket;
    private File tmpDir = null;
    private MailToFtp storeMail = null;

    private enum code // identifica los distintos tipos de mensajes
    {
        compra,
        alerta,
    }

    private static code codeMSG = code.compra;

    MailThread(Socket socket)
    {
        this.socket = socket;
    }

    MailThread(Socket socket, String msgAsunto, String msgCuerpo)
    {
        this.socket = socket;
        this.msgAsunto = msgAsunto;
        this.msgCuerpo = msgCuerpo;
    }

    @Override
    public void run()
    {
        try
        {
            msgManagement();
            if (eCode == errorCode.exito)
            {
                enviarCorreo(msgAsunto, msgCuerpo);
                logger.info(
                        "Correo Enviado con exito a [" + destinatario + "] con el codigo de mensaje [" + codeMSG + "]");
                storeMail = new MailToFtp(tmpDir, fileName);
                storeMail.ftpUpdate();
            }

        }
        catch (DocumentException e)
        {
            eCode = errorCode.doc_error;
            logger.error("Error code " + eCode + " - DocumentException");
            e.printStackTrace();
        }
        catch (SendFailedException e)
        {
            eCode = errorCode.null_account;
            logger.error("Error code " + eCode + " - SendFailedException");
            e.printStackTrace();
        }
        catch (IOException e)
        {
            eCode = errorCode.generic;
            logger.error("Error code " + eCode + " - IO Exception");
            e.printStackTrace();
        }
        catch (MessagingException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
            eCode = errorCode.msg_badFormat;
            logger.error("Error code " + eCode + " - Message error");
        }
    }

    /***
     * Encargado de crear propiedades de un correo y enviarlo por el host definido
     * al inicio de la clase usando un dominio de GMAIL
     * 
     * @param asunto
     * @param cuerpo
     * @throws DocumentException
     * @throws IOException
     * @throws MessagingException
     */
    private void enviarCorreo(String asunto, String cuerpo)
            throws DocumentException, IOException, MessagingException
    {
        Properties props = System.getProperties();
        props.put("mail.smtp.host", host); // El servidor SMTP de Google
        props.put("mail.smtp.user", user); // El usuario de la cuenta BOLETAZO
        props.put("mail.smtp.clave", pass); // La clave de la cuenta BOLETAZO
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.port", port); // El puerto SMTP seguro de Google
        props.put("mail.smtp.socketFactory.port", "465");
        props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.auth", "true"); // Usar autenticación mediante usuario y clave,
                                             // Desactivarlo desde google, "APLICACIONES MENOS SEGURAS"
        props.put("mail.smtp.starttls.enable", "true"); // Para conectar de manera segura al servidor SMTP

        Session session = Session.getDefaultInstance(props);
        // creacion del mensaje MIME
        MimeMessage message = new MimeMessage(session);
        switch (codeMSG)
        {
        case compra:
            enviaMsgCompra(message, session);
            break;
        case alerta:
            enviaMsgAlerta(message, session);
            break;
        }
    }

    /**
     * Usado principalmente por el Monitor SNMP para realizar los mensajes de alerta
     * al administrador
     * 
     * @param message
     * @param session
     * @throws MessagingException
     */
    private void enviaMsgAlerta(MimeMessage message, Session session) throws MessagingException
    {
        message.setFrom(new InternetAddress(user));
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(destinatario));
        message.setSubject(msgAsunto);
        // parte del mensaje de texto plano
        BodyPart txtPart = new MimeBodyPart();
        txtPart.setText(msgCuerpo);

        Transport transport = session.getTransport("smtp");
        transport.connect(host, port, user, pass);
        transport.sendMessage(message, message.getAllRecipients());
        transport.close();
    }

    /**
     * Envia mensaje de confirmacion de compra al usuario, un archivo PDF adjunto
     * con su boleto y detalles del evento solicitado
     * 
     * @param message
     * @param session
     * @throws IOException
     * @throws DocumentException
     */
    private void enviaMsgCompra(MimeMessage message, Session session) throws IOException, DocumentException
    {
        try
        {
            fileName = "Compra" + userID + "-" + eventID + "-" + seatID + ".pdf";
            tmpDir = new File("D:\\opt\\PDFBoletazo\\CorreosEnviados\\" + fileName);

            appendPdf(eventID, userID);

            message.setFrom(new InternetAddress(user));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(destinatario));
            message.setSubject(msgAsunto);

            // parte del mensaje de texto plano
            BodyPart txtPart = new MimeBodyPart();
            txtPart.setText(msgCuerpo);

            // parte del mensaje de PDF
            BodyPart pdfPart = new MimeBodyPart();
            DataSource source = new FileDataSource(tmpDir);
            pdfPart.setDataHandler(new DataHandler(source));
            pdfPart.setFileName(fileName);

            Multipart multiparte = new MimeMultipart();
            multiparte.addBodyPart(txtPart);
            multiparte.addBodyPart(pdfPart);

            message.setContent(multiparte);
            Transport transport = session.getTransport("smtp");
            transport.connect(host, port, user, pass);
            transport.sendMessage(message, message.getAllRecipients());
            transport.close();

        }
        catch (MessagingException me)
        {
            me.printStackTrace(); // Si se produce un error
        }
    }

    /***
     * Genera un nuevo PDF basado en un archivo base, esta funcion ademas modifica
     * el PDF basados en los datos de compra del cliente
     * 
     * @param event
     * @param userID
     * @throws IOException
     * @throws DocumentException
     */
    private void appendPdf(String event, String userID)
            throws IOException, DocumentException
    {
        Date date = new Date();

        Image img_selloValidez = Image.getInstance(IMG_SELLO_PATH);
        PdfReader reader = new PdfReader(PDF_BASEPATH); // input PDF
        PdfStamper stamper = new PdfStamper(reader,
                new FileOutputStream(tmpDir)); // output PDF
        for (int i = 1; i <= reader.getNumberOfPages(); i++)
        {
            PdfContentByte over = stamper.getOverContent(i);

            writeOn(event, over, 180, 540);
            writeOn("Cliente: " + userID, over, 180, 530);
            writeOn("Asiento: " + seatID, over, 180, 520);
            writeOn("Fecha: " + dateEvent, over, 180, 510);
            writeOn("Compra realizada con exito - " + DATE_FORMAT.format(date) + "  Hora de México",
                    over, 100, 70);
            writeOnImage(img_selloValidez, date, over, 100, 100);
        }
        stamper.close();
    }

    /**
     * Escribe sobre el PDF, el texto indicado, en las posiciones indicadas y todo
     * sobre el archivo de escritura PDF indicado
     * 
     * @param text
     * @param over
     * @param posX
     * @param posY
     * @throws DocumentException
     * @throws IOException
     */
    private void writeOn(String text, PdfContentByte over, int posX, int posY) throws DocumentException, IOException
    {
        BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED); // set font
        over.beginText();
        over.setFontAndSize(bf, 10);
        over.setTextMatrix(posX, posY); // Posicion relacionada al nombre del evento.
        over.showText(text); // Texto del nombre del evento SOPORTA 40 CARACTERES
        over.endText();
    }

    /**
     * Inserta una imagen dentro del archivo PDF especificado en las posiciones
     * establecidas
     * 
     * @param imagen
     * @param date
     * @param over
     * @param posX
     * @param posY
     * @throws DocumentException
     * @throws IOException
     */
    private void writeOnImage(Image imagen, Date date, PdfContentByte over, int posX, int posY)
            throws DocumentException, IOException
    {
        BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED); // set font
        over.beginText();
        over.setFontAndSize(bf, 10);
        imagen.setAbsolutePosition(posX, posY);
        over.addImage(imagen);
        over.endText();
    }

    /**
     * Separa, verifica e instancia dentro de variables globales, la solicitud
     * recibida del servidor boletazo
     * 
     * @throws IOException
     */
    private void msgManagement() throws IOException
    {
        InputStream inStream = socket.getInputStream();
        DataInputStream dataIn = new DataInputStream(inStream);
        String[] in = new String[MSG_INTEGRITY];
        in = dataIn.readUTF().toString().split(",");
        int tmpCode = Integer.parseInt(in[in.length - 1]);
        destinatario = in[0];

        switch (tmpCode)
        {
        case 0:
            // MSG = ("destinatario", "userID", "eventID","seatID","dateEvent","codeMSG")
            codeMSG = code.compra;
            userID = in[1];
            eventID = in[2];
            seatID = in[3];
            dateEvent = in[4];
            if (MSG_INTEGRITY != in.length)
            {
                eCode = errorCode.msg_integrity;
                logger.error("Error code " + eCode + " - msg_integrity - destinatario [" + in[0] + "]");
            }
            if (destinatario == "" || destinatario == null || destinatario == "null"
                    || !destinatario.contains("@"))
            {
                eCode = errorCode.msg_badFormat;
                logger.error("Error code " + eCode + "msg_badFormat - " +
                        "Destinatario [ " + destinatario + " ], imposible mandar mensaje");
            }
            break;
        case 1:
            // MSG = ("destinatario", "msgAsunto", "msgCuerpo")
            codeMSG = code.alerta;
            eCode = errorCode.exito;
            msgAsunto = in[1];
            msgCuerpo = in[2];
            break;
        default:
            eCode = errorCode.msg_badFormat;
            logger.error("Error code " + eCode + " - msg_badFormat - destinatario [" + destinatario + "]");
            break;
        }

    }

    /**
     * Se encarga de verificar el dominio dentro de la trama de solicitud del
     * servidor boletazo
     * 
     * @param email
     * @return true si es un dominio valido
     */
    private boolean emailVerification(String email)
    {
        String[] parts = email.split("@");
        if (email.length() == 2)
        {
            if (parts[1].equalsIgnoreCase("hotmail.com")) { return true; }
            if (parts[1].equalsIgnoreCase("gmail.com")) { return true; }
            if (parts[1].equalsIgnoreCase("live.com.mx")) { return true; }
            if (parts[1].equalsIgnoreCase("edu.com.mx")) { return true; }
            if (parts[1].equalsIgnoreCase("yahoo.com"))
            {
                return true;
            }
            else
            {
                String[] anotherDomain = parts[1].split(".");
                if (anotherDomain.length > 2) { return true; }
            }
        }
        eCode = errorCode.null_account;
        return false;
    }
}