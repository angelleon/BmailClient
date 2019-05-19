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
    private Socket socket;

    private enum code // identifica los distintos tipos de mensajes
    {
        compra,
    }

    private static code codeMSG = code.compra;

    MailThread(Socket socket)
    {
        this.socket = socket;
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
            }
        }
        catch (DocumentException e)
        {
            eCode = errorCode.doc_error;
            logger.error("Error code " + eCode + " - DocumentException");
            e.printStackTrace();
        }
        catch (IOException e)
        {
            eCode = errorCode.generic;
            logger.error("Error code " + eCode + " - IO Exception");
            e.printStackTrace();
        }
    }

    private void enviarCorreo(String asunto, String cuerpo)
            throws IOException, DocumentException
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
        try
        {
            appendPdf(eventID, userID);
            message.setFrom(new InternetAddress(user));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(destinatario)); // soporta varios
                                                                                               // destinatarios[]
            message.setSubject(asunto);

            // parte del mensaje de texto plano
            BodyPart txtPart = new MimeBodyPart();
            txtPart.setText(cuerpo);

            // parte del mensaje de PDF
            BodyPart pdfPart = new MimeBodyPart();
            String fileName = "Compra de boleto - " + userID + ".pdf";
            DataSource source = new FileDataSource(
                    "D:\\opt\\PDFBoletazo\\CorreosEnviados\\Compra de boleto - " + userID + ".pdf");
            pdfPart.setDataHandler(new DataHandler(source));
            pdfPart.setFileName(fileName);
            // creacion de un objeto multiparte para unir parte texto plano y pdf
            Multipart multiparte = new MimeMultipart();
            multiparte.addBodyPart(txtPart);
            multiparte.addBodyPart(pdfPart);
            // unimos todas las partes en el mensaje MIME
            message.setContent(multiparte);

            Transport transport = session.getTransport("smtp");
            transport.connect(host, port, user, pass);
            transport.sendMessage(message, message.getAllRecipients());
            transport.close();
            File tmpDir = new File("D:\\opt\\PDFBoletazo\\CorreosEnviados\\Compra de boleto - " + userID + ".pdf");
            tmpDir.delete();
        }
        catch (MessagingException me)
        {
            me.printStackTrace(); // Si se produce un error
        }
    }

    private void appendPdf(String event, String userID)
            throws IOException, DocumentException
    {
        String tmpUserPath = "D:\\opt\\PDFBoletazo\\CorreosEnviados\\Compra de boleto - " + userID + ".pdf";
        Date date = new Date();

        Image img_selloValidez = Image.getInstance(IMG_SELLO_PATH);
        PdfReader reader = new PdfReader(PDF_BASEPATH); // input PDF
        PdfStamper stamper = new PdfStamper(reader,
                new FileOutputStream(tmpUserPath)); // output PDF
        // File tmpDir = new File(tmpUserPath); // archivo
        BaseFont bf = BaseFont.createFont(
                BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED); // set font

        // loop on pages (1-based)
        for (int i = 1; i <= reader.getNumberOfPages(); i++)
        {

            // get object for writing over the existing content;
            // you can also use getUnderContent for writing in the bottom layer
            PdfContentByte over = stamper.getOverContent(i);

            // write text
            over.beginText();
            over.setFontAndSize(bf, 10); // Fuente y tamaño configurable
            over.setTextMatrix(180, 540); // Posicion relacionada al nombre del evento.
            over.showText(event); // Texto del nombre del evento SOPORTA 40
                                  // CARACTERES
            over.endText();
            over.setTextMatrix(180, 530); // Posicion relacionada al nombre del evento.
            over.showText("Cliente: " + userID); // texto numero de usuario que compro SOPORTA 40
                                                 // CARACTERES
            over.endText();
            over.setTextMatrix(180, 520); // Posicion relacionada al nombre del evento.
            over.showText("Asiento: " + seatID); // texto numero de usuario que compro SOPORTA 40
                                                 // CARACTERES
            over.endText();
            over.setTextMatrix(180, 510); // Posicion relacionada al nombre del evento.
            over.showText("Fecha: " + dateEvent); // texto numero de usuario que compro SOPORTA 40
                                                  // CARACTERES
            over.endText();
            over.setTextMatrix(100, 70);
            over.showText("Compra realizada con exito - " + DATE_FORMAT.format(date) + "  Hora de México");
            img_selloValidez.setAbsolutePosition(100, 100);
            over.addImage(img_selloValidez);
        }
        stamper.close();
        // tmpDir.delete();
    }

    public void msgManagement() throws IOException
    // MSG = ("destinatario", "userID", "eventID","seatID","dateEvent","codeMSG")
    {
        InputStream inStream = socket.getInputStream();
        DataInputStream dataIn = new DataInputStream(inStream);
        String[] in = new String[MSG_INTEGRITY];
        in = dataIn.readUTF().toString().split(",");

        destinatario = in[0];
        userID = in[1];
        eventID = in[2];
        seatID = in[3];
        dateEvent = in[4];
        if (MSG_INTEGRITY != in.length)
        {
            eCode = errorCode.msg_integrity;
            logger.error("Error code " + eCode + " - msg_integrity - destinatario [" + in[0] + "]");
        }

        int tmpCode = Integer.parseInt(in[5]);
        switch (tmpCode)
        {
        case 0:
            codeMSG = code.compra;
            break;
        default:
            eCode = errorCode.msg_badFormat;
            logger.error("Error code " + eCode + " - msg_badFormat - destinatario [" + destinatario + "]");
            break;
        }
    }
}