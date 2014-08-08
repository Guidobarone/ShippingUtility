package shippingutility;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.Vector;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class ShippingUtility {

	private static final String CONFIGURATION_FILE = "ShippingUtility.properties";
	private static String databaseIp = "";
	private static String databaseName = "";
	private static String databaseUser = "";
	private static String databasePassword = "";
	private static String workFolder = "";
	private static String SMTP_HOST_NAME = "";
	private static String SMTP_AUTH_USER = "";
	private static String SMTP_AUTH_PWD = "";
	private static String sede = "";
	private static String codiceCliente = "";
	private static String indirizzo = "";
	private static String senderMail = "";
	private static String ccMail1 = "";
	private static String ccMail2 = "";
	private static String subject = "";
	
	public static void main(String[] args) throws Exception {
		
		// read properties file
		Properties properties = new Properties();
		try {
		    properties.load(new FileInputStream(CONFIGURATION_FILE));
		} catch (IOException e) {
			System.err.println("Error: error loading configuration file " + CONFIGURATION_FILE);
		}

		databaseIp = properties.getProperty("DATABASEIP");
		databaseName = properties.getProperty("DATABASENAME");
		databaseUser = properties.getProperty("DATABASEUSER");
		databasePassword = properties.getProperty("DATABASEPASSWORD");
		workFolder = properties.getProperty("WORKFOLDER");
		SMTP_HOST_NAME = properties.getProperty("SMTP_HOST_NAME");
		SMTP_AUTH_USER = properties.getProperty("SMTP_AUTH_USER");
		SMTP_AUTH_PWD = properties.getProperty("SMTP_AUTH_PWD");
		sede = properties.getProperty("SEDE");
		codiceCliente = properties.getProperty("CODICECLIENTE");
		indirizzo = properties.getProperty("INDIRIZZO");
		senderMail = properties.getProperty("SENDERMAIL");
		ccMail1 = properties.getProperty("CCMAIL1");
		ccMail2 = properties.getProperty("CCMAIL2");
		//subject = properties.getProperty("SUBJECT");

		if (args.length >= 2)
		{
			Connection conn = getConnection();

			if (args[1].equals("distinta"))
			{
				createShippingDocFile(args[0], conn);
			}
			else if (args[1].equals("tracciato"))
			{
				sendShippingMail(args[0], conn);
			}
			else
			{
				System.err.println("Error: comando non gestito: "+args[1]);
			}
		}
		else
		{
			System.err.println("Error: inseriti meno di 2 parametri");
		}
	}
	
	private static void createShippingDocFile(String shipping_id, Connection conn) throws Exception {
		
		int id_corriere = getShippingCourier(shipping_id, conn);
		String nome_corriere = getCourierName(id_corriere, conn);
		int packagesCount = countShippingPackages(shipping_id, conn);
		String pesoTot = (getShippingWeight(shipping_id, conn)+"").replace('.', ',');
		Vector<String> packagesVect = getShippingPackages(shipping_id, conn);
		
		File file = new File(workFolder+"/"+shipping_id+".txt");
		
		FileWriter fw = new FileWriter(file);
		BufferedWriter bw = new BufferedWriter(fw);
		
		bw.write("Sede:			"+sede);
		bw.newLine();
		bw.write("Codice Cliente:		"+codiceCliente);
		bw.newLine();
		bw.write("Numero Spedizione:	"+shipping_id);
		bw.newLine();
		bw.write("Destinatario:		"+nome_corriere);
		bw.newLine();
		bw.write("Indirizzo:		"+indirizzo);
		bw.newLine();
		bw.write("Numero Colli:		"+packagesCount);
		bw.newLine();
		bw.write("Peso Totale:		"+pesoTot);
		bw.newLine();
		bw.newLine();
		bw.write("Dettaglio colli inclusi nella spedizione:");
		bw.newLine();
		bw.newLine();
		
		for (int i=0; i<packagesVect.size(); i++) {
			bw.write(packagesVect.get(i));
			bw.newLine();
	    }
		bw.close();
	}
	
	private static void sendShippingMail(String shipping_id, Connection conn) throws Exception {
		
		File shippingFile = createShippingDetailsFile(shipping_id, conn);
		int id_corriere = getShippingCourier(shipping_id, conn);
		String nome_corriere = getCourierName(id_corriere, conn);
		String email = getCourierEmail(id_corriere, conn);
		String[] recipientsArray = email.split(",");
		sendMailToCourier(shippingFile, recipientsArray, nome_corriere);
	}
	
	private static File createShippingDetailsFile(String shipping_id, Connection conn) throws Exception {
		
		Vector<String> shippingDetailsVect = getShippingDetailsVect(shipping_id, conn);
		
		File file = new File(workFolder+"/"+shipping_id+".csv");
		
		FileWriter fw = new FileWriter(file);
		BufferedWriter bw = new BufferedWriter(fw);
		for (int i=0; i<shippingDetailsVect.size(); i++) {
			bw.write(shippingDetailsVect.get(i));
			bw.newLine();
	    }
		bw.close();
		
		return file;
	}
	
	private static Vector<String> getShippingDetailsVect(String shipping_id, Connection conn) throws Exception {
		
		System.out.println("getShippingDetailsVect "+shipping_id);

		CallableStatement cstmt = null;
		
		Vector <String> vres= new Vector<String>();
		
	    String sProc = "call P_Ship_View_1_GetShippingDetails(?)";
	    cstmt = conn.prepareCall(sProc);
	    cstmt.setString(1, shipping_id);

	    ResultSet rs = cstmt.executeQuery();

	    String elem = "";
	    while (rs.next()) {
	    	
	    	elem = "";
		    if (rs.getString(1) != null)
		    	elem += rs.getString(1).replace(";", " ");	// ragione sociale
		    elem += ";";
		    
		    if (rs.getString(2) != null)
		    	elem += rs.getString(2).replace(";", " ");	//indirizzo
		    elem += ";";
		    
		    if (rs.getString(3) != null)
		    	elem += rs.getString(3).replace(";", " ");	//citta
		    elem += ";";
		    
		    if (rs.getString(4) != null)
		    	elem += rs.getString(4).replace(";", " ");	//zip
		    elem += ";";
		    
		    if (rs.getString(5) != null)
		    	elem += rs.getString(5).replace(";", " ");	//provincia
		    elem += ";";
		    
		    if (rs.getString(6) != null)
		    	elem += rs.getString(6).replace(";", " ");		// numero ordine
	    	elem += ";";
	    	
		    if (rs.getString(7) != null)
		    	elem += rs.getString(7).replace(";", " ");	
		    elem += ";";
		    
		    if (rs.getString(8) != null)
		    	elem += rs.getString(8).replace(";", " ");	//colli
		    elem += ";";
		    
		    if (rs.getString(9) != null)
		    	elem += rs.getString(9).replace(";", " ");	//incoterm
		    elem += ";";
		    
		    if (rs.getString(10) != null)
		    	elem += rs.getString(10).replace(";", " ").replace('.', ',');		// peso reale
		    elem += ";";
		    
		    if (rs.getString(11) != null)
		    	elem += rs.getString(11).replace(";", " ").replace('.', ',');		// importo contrassegno
		    elem += ";";
		    
		    if (rs.getString(12) != null)
		    	elem += rs.getString(12).replace(";", " ");	 //note_spedizione
		    elem += ";";
		    
		    if (rs.getString(13) != null)
		    	elem += rs.getString(13).replace(";", " ");	
		    elem += ";";
		    
		    if (rs.getString(14) != null)
		    	elem += rs.getString(14).replace(";", " ");	
		    elem += ";";
		    
		    if (rs.getString(15) != null)
		    	elem += rs.getString(15).replace(";", " ");	
		    elem += ";";

		    if (rs.getString(16) != null)
		    	elem += rs.getString(16).replace(";", " ").replace('.', ',');		// peso volume
		    elem += ";";
		    
		    if (rs.getString(17) != null)
		    	elem += rs.getString(17).replace(";", " ");	
		    elem += ";";
		    
		    if (rs.getString(18) != null)
		    	elem += rs.getString(18).replace(";", " ");	
		    elem += ";";
		    
		    if (rs.getString(19) != null)
		    	elem += rs.getString(19).replace(";", " ");	
		    elem += ";";
		    
		    if (rs.getString(20) != null)
		    	elem += rs.getString(20).replace(";", " ");	
		    elem += ";";
		    
		    if (rs.getString(21) != null)
		    	elem += rs.getString(21).replace(";", " ");	
		    elem += ";";
		    
		    if (rs.getString(22) != null)
		    	elem += rs.getString(22).replace(";", " ").replace('.', ',');		// valore merce
		    elem += ";";
		    
		    if (rs.getString(23) != null)
		    	elem += rs.getString(23).replace(";", " ");	
		    elem += ";";
		    
		    if (rs.getString(24) != null)
		    	elem += rs.getString(24).replace(";", " ");	
		    elem += ";";
		    
		    if (rs.getString(25) != null)
		    	elem += rs.getString(25).replace(";", " ");	
		    elem += ";";
		    
		    if (rs.getString(26) != null)
		    	elem += rs.getString(26).replace(";", " ");	
		    elem += ";";
		    
		    if (rs.getString(27) != null)
		    	elem += rs.getString(27).replace(";", " ");	
		    elem += ";";
		    
		    if (rs.getString(28) != null)
		    	elem += rs.getString(28).replace(";", " ");	
		    elem += ";";
		    
		    if (rs.getString(29) != null)
		    	elem += rs.getString(29).replace(";", " ");	
		    
		    vres.add(elem);
	    }
	    System.out.println("P_Ship_View_1_GetShippingDetails executed");
	    for(int i=0;i<vres.size(); i++)
	    	System.out.println(vres.get(i));
	    
	    cstmt.close();
	    
		return vres;
	}
	
	private static int getShippingCourier(String shipping_id, Connection conn) throws Exception {
		
		System.out.println("getShippingCourier "+shipping_id);

		CallableStatement cstmt = null;
		
	    String sProc = "call P_Ship_View_4_GetShippingCourier(?)";
	    cstmt = conn.prepareCall(sProc);
	    cstmt.setString(1, shipping_id);
	    
	    ResultSet rs = cstmt.executeQuery();
	    int result = 0;
	    while (rs.next()){
		    result = rs.getInt(1);
	    }
	    System.out.println("P_Ship_View_4_GetShippingCourier executed");
	    System.out.println(result);
	    
	    cstmt.close();
	    
	    return result;
	}
	
	private static String getCourierEmail(int id_corriere, Connection conn)  throws Exception {
		
		System.out.println("getCourierEmail "+id_corriere);

		CallableStatement cstmt = null;
		
	    String sProc = "call P_Ship_View_5_GetCourierInfo(?)";
	    cstmt = conn.prepareCall(sProc);
	    cstmt.setInt(1, id_corriere);
	    
	    ResultSet rs = cstmt.executeQuery();
	    String result = "";
	    while (rs.next()){
		    result += rs.getString(3);
		    subject += rs.getString(7); //acquisizione del subject
	    }
	    System.out.println("P_Ship_View_5_GetCourierInfo executed");
	    System.out.println(result);
	    
	    cstmt.close();
	    
	    return result;
	}
	
	private static void sendMailToCourier(File attachment, String[] recipients, String nome_corriere) throws Exception {
		
		Properties props = new Properties();
		props.put("mail.transport.protocol", "smtp");
		props.put("mail.smtp.host", SMTP_HOST_NAME);
		props.put("mail.smtp.auth", "true");
		
		// solo per gmail
//		props.put("mail.smtp.starttls.enable", "true");
//		props.put("mail.smtp.port", "587");
		
		Authenticator auth = new SMTPAuthenticator();
		Session mailSession = Session.getDefaultInstance(props, auth);
		// uncomment for debugging infos to stdout
//		mailSession.setDebug(true);

		//create the message 
		MimeMessage message = new MimeMessage(mailSession);

		//mittente
		if (senderMail != null && senderMail.length() > 0)
			message.setFrom(new InternetAddress(senderMail));

		//destinatari
		for (int i=0; i<recipients.length; i++)
		{
			recipients[i] = recipients[i].trim();
			if (recipients[i] != null && recipients[i].length() > 0 && !recipients[i].equalsIgnoreCase("null"))
			{
				message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipients[i]));
			}
		}
		
		//carbon copy
		if (ccMail1 != null && ccMail1.length() > 0)
			message.addRecipient(Message.RecipientType.CC, new InternetAddress(ccMail1));
		if (ccMail2 != null && ccMail2.length() > 0)
			message.addRecipient(Message.RecipientType.CC, new InternetAddress(ccMail2));

		//oggetto
		if (subject != null && subject.length() > 0)
			message.setSubject(subject);
		else
			message.setSubject("DBWEB");

		//create the message content
		Multipart multipart = new MimeMultipart();

		// part 1) message body
		MimeBodyPart messageBodyPart = new MimeBodyPart();
		messageBodyPart.setText("In allegato il tracciato della spedizione #"
								+attachment.getName().substring(0,attachment.getName().length()-4)
								+" del "+new SimpleDateFormat("yyyy-MM-dd").format(new Date())
								+" ("+nome_corriere+")."
								+"\n\nDistinti saluti,\n\nwww.minimegaprint.com");
		multipart.addBodyPart(messageBodyPart);

		// part 2) attachment
		messageBodyPart = new MimeBodyPart();
		DataSource source = new FileDataSource(attachment);
		messageBodyPart.setDataHandler(new DataHandler(source));
		messageBodyPart.setFileName(attachment.getName());
		multipart.addBodyPart(messageBodyPart);

		// put parts in message
		message.setContent(multipart);

		// send the message
		Transport transport = mailSession.getTransport();
		transport.connect();
//		System.out.println("message.getAllRecipients()="+message.getAllRecipients().toString());
		transport.sendMessage(message, message.getAllRecipients());
		transport.close();
	}

	private static class SMTPAuthenticator extends javax.mail.Authenticator {
		public PasswordAuthentication getPasswordAuthentication() {
			String username = SMTP_AUTH_USER;
			String password = SMTP_AUTH_PWD;
			return new PasswordAuthentication(username, password);
		}
	}
	
	private static String getCourierName(int id_corriere, Connection conn)  throws Exception {
		
		System.out.println("getCourierName "+id_corriere);

		CallableStatement cstmt = null;
		
	    String sProc = "call P_Ship_View_5_GetCourierInfo(?)";
	    cstmt = conn.prepareCall(sProc);
	    cstmt.setInt(1, id_corriere);
	    
	    ResultSet rs = cstmt.executeQuery();
	    String result = "";
	    while (rs.next()){
		    result += rs.getString(2);
	    }
	    System.out.println("P_Ship_View_5_GetCourierInfo executed");
	    System.out.println(result);
	    
	    cstmt.close();
	    
	    return result;
	}
	
	private static Vector <String> getShippingPackages(String shipping_id, Connection conn) throws Exception {
		
		System.out.println("getPackagesInShipping "+shipping_id);

		CallableStatement cstmt = null;
		
		Vector <String> vres= new Vector<String>();
		
	    String sProc = "call P_Ship_View_2_GetShippingPackages(?)";
	    cstmt = conn.prepareCall(sProc);
	    cstmt.setString(1, shipping_id);
	    
	    ResultSet rs = cstmt.executeQuery();
	    
	    String elem = "";
	    while (rs.next()) {
	    	elem = rs.getString(1)+"\t\t";
//	    	elem += rs.getString(2)+"\t\t";
	    	if (rs.getString(3) != null)
	    		elem += (rs.getString(3)+"").replace('.', ',');
	    	vres.add(elem);
	    }
	    System.out.println("P_Ship_View_2_GetShippingPackages executed");
	    for(int i=0;i<vres.size(); i++)
	    	System.out.println(vres.get(i));
	    
	    cstmt.close();
	    
	    return vres;
	}
	
	private static int countShippingPackages(String shipping_id, Connection conn) throws Exception {
		
		System.out.println("countPackagesInShipping "+shipping_id);

		CallableStatement cstmt = null;
		
	    String sProc = "call P_Ship_View_2_GetShippingPackages(?)";
	    cstmt = conn.prepareCall(sProc);
	    cstmt.setString(1, shipping_id);
	    
	    ResultSet rs = cstmt.executeQuery();
	    
	    int count = 0;
	    while (rs.next()) {
		    count++;
	    }
	    System.out.println("P_Ship_View_2_GetShippingPackages executed");
	    System.out.println(count);
	    
	    cstmt.close();
	    
	    return count;
	}
	
	private static float getShippingWeight(String shipping_id, Connection conn) throws Exception {
		
		System.out.println("getShippingWeight "+shipping_id);

		CallableStatement cstmt = null;
		
	    String sProc = "call P_Ship_View_2_GetShippingPackages(?)";
	    cstmt = conn.prepareCall(sProc);
	    cstmt.setString(1, shipping_id);
	    
	    ResultSet rs = cstmt.executeQuery();
	    
	    float weight = 0;
	    while (rs.next()) {
	    	weight += rs.getFloat(3);
	    }
	    System.out.println("P_Ship_View_2_GetShippingPackages executed");
	    System.out.println(weight);
	    
	    cstmt.close();
	    
	    return weight;
	}

    private static Connection getConnection() {

        try {
            Connection conn = DriverManager.getConnection("jdbc:mysql://"+databaseIp+
                                                                      "/"+databaseName+
                                                                 "?user="+databaseUser+
                                                             "&password="+databasePassword);
            return conn;

        } catch (Exception e) {
                e.printStackTrace();
        }
        return null;
    }
}