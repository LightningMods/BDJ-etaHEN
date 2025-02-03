package org.homebrew;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import java.awt.BorderLayout;
import java.awt.event.KeyEvent;
import javax.tv.xlet.Xlet;
import javax.tv.xlet.XletContext;
import org.havi.ui.HScene;
import org.havi.ui.HSceneFactory;
import org.havi.ui.event.HRcEvent;
import org.dvb.event.EventManager;
import org.dvb.event.UserEvent;
import org.dvb.event.UserEventListener;
import org.dvb.event.UserEventRepository;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class MyXlet implements UserEventListener, Xlet {
    private static String ELFLDR_URL  = "https://github.com/ps5-payload-dev/elfldr/releases/latest/download/Payload.zip";
    private static String ETAHEN_URL  = "https://github.com/etaHEN/etaHEN/releases/latest/download/Payload.zip";


    private HScene scene;
    private LoggingUI logUI;
    private ListUI listUI;
    private UserEventRepository evtRepo;

    private static byte[] readBytes(InputStream is) throws Exception {
	ByteArrayOutputStream buf = new ByteArrayOutputStream();
	byte[] chunk = new byte[0x4000];

        while (true) {
            int length = is.read(chunk, 0, chunk.length);
            if (length < 0) {
                break;
            } else {
                buf.write(chunk, 0, length);
            }
        }

        return buf.toByteArray();
    }

    private byte[] fetchPayload(String uri) throws Exception {
	if(uri.startsWith("/")) {
	    File file = new File(uri);
	    InputStream is = new FileInputStream(file);
	    return readBytes(is);
	}

	if(uri.startsWith("https://")) {
	    URL url = new URL(uri);
	    HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
	    SSLContext ctx = SSLContext.getInstance("TLSv1.2");

	    ctx.init(null, null, null);
	    conn.setSSLSocketFactory(ctx.getSocketFactory());

	    ZipInputStream is = new ZipInputStream(conn.getInputStream());
	    ZipEntry entry = is.getNextEntry();

	    return readBytes(is);
	}
	throw new Exception("Unknown URI " + uri);
    }

     private void addPayload(String title, String uri) {
	listUI.addItem(title,
		       new Runnable() {
			   public void run() {
			       try {
				  ElfLoading.runElf(fetchPayload(uri));
			       } catch (Throwable t) {
				   libkernel.sendNotificationRequest(t.getMessage());
				   LoggingUI.getInstance().log(t);
				   logUI.setVisible(true);
			       }
			   }
		       });
     }


    public void initXlet(XletContext context) {
	logUI = LoggingUI.getInstance();
	logUI.setSize(1280, 720);

	listUI = new ListUI();
	listUI.setSize(1280, 720);

	evtRepo = new UserEventRepository("input");
	evtRepo.addKey(HRcEvent.VK_ENTER);
	evtRepo.addKey(HRcEvent.VK_UP);
	evtRepo.addKey(HRcEvent.VK_DOWN);
	evtRepo.addKey(461); // □

	scene = HSceneFactory.getInstance().getDefaultHScene();
	scene.add(logUI, BorderLayout.CENTER);
	scene.add(listUI, BorderLayout.CENTER);
        scene.validate();
	scene.repaint();
    }

    private static boolean isServerUp(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void startXlet() {
        scene.setVisible(true);

	try {
	    LoggingUI.getInstance().log("[*] Disabling Java security manager...");
	    PrivilegeEscalation.disableSecurityManager();
	    LoggingUI.getInstance().log("[+] Java security manager disabled");

	    if(isServerUp("localhost", 9021)) {
		LoggingUI.getInstance().log("[+] elfldr.elf is already running, skipping kernel exploit");
	    } else {
		LoggingUI.getInstance().log("[*] Obtaining kernel .data R/W capabilities...");
		KernelMemory.enableRW();
		LoggingUI.getInstance().log("[+] Kernel .data R/W achieved");
	     
		KernelPatching.escalatePrivileges();
		KernelPatching.setSceAuthId(0x4801000000000013l);
		KernelPatching.setSceCaps(0xffffffffffffffffl, 0xffffffffffffffffl);
		KernelPatching.setSceAttr(KernelPatching.getSceAttr() | 0x80);
		LoggingUI.getInstance().log("[+] Escalated privileges");

		KernelPatching.setSecurityFlags(KernelPatching.getSecurityFlags() | 0x14);
		KernelPatching.setUtokenFlags((byte)(KernelPatching.getUtokenFlags() | 0x1));
		KernelPatching.setQAFlags(KernelPatching.getQAFlags() | 0x0000000000010300l);
		KernelPatching.setTargetId((byte)0x82);
		LoggingUI.getInstance().log("[+] Debug/dev mode enabled");

		//try {
		//    LoggingUI.getInstance().log("[*] Loading " + ELFLDR_URL);
		//    ElfLoading.runElf(fetchPayload(ELFLDR_URL));
		//} catch (Exception ex) {
		//    LoggingUI.getInstance().log(ex);
		   // LoggingUI.getInstance().log("[*] Loading /disc/elfldr.elf");
		   // ElfLoading.runElf(fetchPayload("/disc/elfldr.elf"));
		//}
	    }

	    EventManager.getInstance().addUserEventListener(this, evtRepo);
	} catch (Throwable t) {
	    LoggingUI.getInstance().log(t);
	}

	listUI.addItem("Payloads from https://github.com/etaHEN");
	addPayload("etaHEN (Latest GitHub Build) - AIO HEN payload", ETAHEN_URL);

	listUI.addItem("Payloads from disc");
	addPayload("etaHEN 2.0B Pre-Release - AIO HEN payload", "/disc/etaHEN.elf");

	logUI.setVisible(false);
    }

    public void pauseXlet() {
	scene.setVisible(false);
    }

    public void destroyXlet(boolean unconditional) {
        scene = null;
    }

    public void userEventReceived(UserEvent evt) {
	if(evt.getType() != KeyEvent.KEY_RELEASED) {
	    return;
	}

	if(evt.getCode() == 461) {
	    logUI.setVisible(!logUI.isVisible());
	    return;
	}

	switch(evt.getCode()) {
	case HRcEvent.VK_ENTER:
	    listUI.itemActivate();
	    break;
	case HRcEvent.VK_UP:
	    listUI.itemUp();
	    break;
	case HRcEvent.VK_DOWN:
	    listUI.itemDown();
	    break;
	}
    }
}
