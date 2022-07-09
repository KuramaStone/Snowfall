package me.brook.selection.tools;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.UIManager;

public class HideToSystemTray extends JFrame {
	private static final long serialVersionUID = 2871573343110797511L;

	TrayIcon trayIcon;
	SystemTray tray;

	public HideToSystemTray(BufferedImage bufferedImage) {
		super("SystemTray test");
		System.out.println("creating instance");
		try {
			System.out.println("setting look and feel");
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch(Exception e) {
			System.out.println("Unable to set LookAndFeel");
		}
		if(SystemTray.isSupported()) {
			System.out.println("system tray supported");
			tray = SystemTray.getSystemTray();

			Image image = null;
			image = bufferedImage;
			
			ActionListener exitListener = new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					System.out.println("Exiting....");
					System.exit(0);
				}
			};
			PopupMenu popup = new PopupMenu();
			MenuItem defaultItem = new MenuItem("Exit");
			defaultItem.addActionListener(exitListener);
			popup.add(defaultItem);
			defaultItem = new MenuItem("Open");
			defaultItem.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					setVisible(true);
					setExtendedState(JFrame.NORMAL);
				}
			});
			popup.add(defaultItem);
			trayIcon = new TrayIcon(image, "SystemTray Demo", popup);
			trayIcon.setImageAutoSize(true);
		}
		else {
			System.out.println("system tray not supported");
		}
		addWindowStateListener(new WindowStateListener() {
			public void windowStateChanged(WindowEvent e) {
				if(e.getNewState() == ICONIFIED) {
					addToTray();
				}
				if(e.getNewState() == 7) {
					addToTray();
				}
				if(e.getNewState() == MAXIMIZED_BOTH) {
					removeFromTray();
				}
				if(e.getNewState() == NORMAL) {
					removeFromTray();
				}
			}
		});
		setIconImage(bufferedImage);
	}

	protected void removeFromTray() {
		tray.remove(trayIcon);
		setVisible(true);
		System.out.println("Tray icon removed");
	}

	protected void addToTray() {
		try {
			tray.add(trayIcon);
			setVisible(false);
			System.out.println("added to SystemTray");
		}
		catch(AWTException ex) {
			System.out.println("unable to add to tray");
		}
	}

}