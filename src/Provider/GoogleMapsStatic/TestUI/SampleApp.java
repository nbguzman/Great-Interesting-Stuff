/**
 * This project is about building a Geographical Information System (GIS) 
 * application using Java Swing Technology. It will integrate Google Maps
 * with the application. Thanks to Nazmul Idris, the basic design and
 * functionality have been covered. The reason for this project is to add
 * on more functionality and features such as:
 * <ul>
 * <li>Panning and Zooming using keyboard keys, mouse scroll wheel and JButton
 * <li>Way-pointing system
 * <li>Saving map images to a specified directory
 * <li>Adding in user's IP and relevant information
 * <li>Implementing app and map in the same dialog panel
 * <li>Using a country name listing to show quick access to its location
 * </ul>
 * <p>
 * 
 * @author      Nazmul Idris
 * @author      Neil Brian Guzman
 * @version     1.0
 */

package Provider.GoogleMapsStatic.TestUI;

import Provider.GoogleMapsStatic.*;
import Task.*;
import Task.Manager.*;
import Task.ProgressMonitor.*;
import Task.Support.CoreSupport.*;
import Task.Support.GUISupport.*;
import com.jgoodies.forms.factories.*;
import info.clearthought.layout.*;
import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.beans.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.text.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.*;
import jxl.Cell;
import jxl.CellType;
import jxl.Sheet;
import jxl.Workbook;
import jxl.read.biff.BiffException;

public class SampleApp extends JFrame {
	private static final String RAWTYPES = "rawtypes";
	// XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
	// data members
	// XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
	/** reference to task */
	private SimpleTask _task;
	/** this might be null. holds the image to display in a popup */
	private BufferedImage _img;
	/** this might be null. holds the text in case image doesn't display */
	private String _respStr;

	// XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
	// main method...
	// XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX

	public static void main(String[] args) {

		Utils.createInEDT(SampleApp.class);
	}

	// XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
	// constructor
	// XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX

	private void doInit() {
		GUIUtils.setAppIcon(this, "burn.png");
		GUIUtils.centerOnScreen(this);
		setVisible(true);

		int W = 28, H = W;
		boolean blur = false;
		float alpha = .7f;

		try {
			btnGetMap.setIcon(ImageUtils.loadScaledBufferedIcon("ok1.png", W,
					H, blur, alpha));
			btnQuit.setIcon(ImageUtils.loadScaledBufferedIcon("charging.png",
					W, H, blur, alpha));
		} catch (Exception e) {
			System.out.println(e);
		}

		_setupTask();
	}

	/**
	 * create a test task and wire it up with a task handler that dumps output
	 * to the textarea
	 */
	@SuppressWarnings("unchecked")
	private void _setupTask() {

		TaskExecutorIF<ByteBuffer> functor = new TaskExecutorAdapter<ByteBuffer>() {
			public ByteBuffer doInBackground(Future<ByteBuffer> swingWorker,
					SwingUIHookAdapter hook) throws Exception {

				_initHook(hook);

				String uri = MapLookup.getMap(
						Double.parseDouble(ttfLat.getText()),
						Double.parseDouble(ttfLon.getText()),
						Integer.parseInt(ttfSizeW.getText()),
						Integer.parseInt(ttfSizeH.getText()),
						Integer.parseInt(ttfZoom.getText()));
				sout("Google Maps URI=" + uri);

				// get the map from Google
				GetMethod get = new GetMethod(uri);
				new HttpClient().executeMethod(get);

				ByteBuffer data = HttpUtils.getMonitoredResponse(hook, get);

				try {
					_img = ImageUtils.toCompatibleImage(ImageIO.read(data
							.getInputStream()));
					sout("converted downloaded data to image...");
				} catch (Exception e) {
					_img = null;
					sout("The URI is not an image. Data is downloaded, can't display it as an image.");
					_respStr = new String(data.getBytes());
				}

				return data;
			}

			@Override
			public String getName() {
				return _task.getName();
			}
		};

		_task = new SimpleTask(new TaskManager(), functor, "HTTP GET Task",
				"Download an image from a URL", AutoShutdownSignals.Daemon);

		_task.addStatusListener(new PropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent evt) {
				sout(":: task status change - "
						+ ProgressMonitorUtils.parseStatusMessageFrom(evt));
				lblProgressStatus.setText(ProgressMonitorUtils
						.parseStatusMessageFrom(evt));
			}
		});

		_task.setTaskHandler(new SimpleTaskHandler<ByteBuffer>() {
			@Override
			public void beforeStart(AbstractTask task) {
				sout(":: taskHandler - beforeStart");
			}

			@Override
			public void started(AbstractTask task) {
				sout(":: taskHandler - started ");
			}

			/**
			 * {@link SampleApp#_initHook} adds the task status listener, which
			 * is removed here
			 */
			@Override
			public void stopped(long time, AbstractTask task) {
				sout(":: taskHandler [" + task.getName() + "]- stopped");
				sout(":: time = " + time / 1000f + "sec");
				task.getUIHook().clearAllStatusListeners();
			}

			@Override
			public void interrupted(Throwable e, AbstractTask task) {
				sout(":: taskHandler [" + task.getName() + "]- interrupted - "
						+ e.toString());
			}

			@Override
			public void ok(ByteBuffer value, long time, AbstractTask task) {
				sout(":: taskHandler [" + task.getName() + "]- ok - size="
						+ (value == null ? "null" : value.toString()));
				if (_img != null) {
					_displayImgInFrame();
				} else
					_displayRespStrInFrame();

			}

			@Override
			public void error(Throwable e, long time, AbstractTask task) {
				sout(":: taskHandler [" + task.getName() + "]- error - "
						+ e.toString());
			}

			@Override
			public void cancelled(long time, AbstractTask task) {
				sout(" :: taskHandler [" + task.getName() + "]- cancelled");
			}
		});
	}

	private SwingUIHookAdapter _initHook(SwingUIHookAdapter hook) {
		hook.enableRecieveStatusNotification(checkboxRecvStatus.isSelected());
		hook.enableSendStatusNotification(checkboxSendStatus.isSelected());

		hook.setProgressMessage(ttfProgressMsg.getText());

		PropertyChangeListener listener = new PropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent evt) {
				SwingUIHookAdapter.PropertyList type = ProgressMonitorUtils
						.parseTypeFrom(evt);
				int progress = ProgressMonitorUtils.parsePercentFrom(evt);
				String msg = ProgressMonitorUtils.parseMessageFrom(evt);

				progressBar.setValue(progress);
				progressBar.setString(type.toString());

				sout(msg);
			}
		};

		hook.addRecieveStatusListener(listener);
		hook.addSendStatusListener(listener);
		hook.addUnderlyingIOStreamInterruptedOrClosed(new PropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent evt) {
				sout(evt.getPropertyName() + " fired!!!");
			}
		});

		return hook;
	}

	private void _displayImgInFrame() {

		final JFrame frame = new JFrame("Google Static Map");
		GUIUtils.setAppIcon(frame, "71.png");
		frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		JLabel imgLbl = new JLabel(new ImageIcon(_img));
		imgLbl.setToolTipText(MessageFormat.format(
				"<html>Image downloaded from URI<br>size: w={0}, h={1}</html>",
				_img.getWidth(), _img.getHeight()));
		imgLbl.addMouseListener(new MouseListener() {
			public void mouseClicked(MouseEvent e) {
			}

			public void mousePressed(MouseEvent e) {
				frame.dispose();
			}

			public void mouseReleased(MouseEvent e) {
			}

			public void mouseEntered(MouseEvent e) {
			}

			public void mouseExited(MouseEvent e) {
			}
		});

		imgLbl.addMouseWheelListener(new MouseWheelListener() {

			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				if (e.getWheelRotation() > 0)
					zoomSlider.setValue(zoomSlider.getValue() + 1);
				else
					zoomSlider.setValue(zoomSlider.getValue() - 1);
			}

		});
		mPanel1.removeAll(); // removes previous image
		mPanel1.add(imgLbl); // adds new image
		mPanel1.repaint(); // redraws the panel
	}

	private void _displayRespStrInFrame() {

		final JFrame frame = new JFrame("Google Static Map - Error");
		GUIUtils.setAppIcon(frame, "69.png");
		frame.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		JTextArea response = new JTextArea(_respStr, 25, 80);
		response.addMouseListener(new MouseListener() {
			public void mouseClicked(MouseEvent e) {
			}

			public void mousePressed(MouseEvent e) {
				frame.dispose();
			}

			public void mouseReleased(MouseEvent e) {
			}

			public void mouseEntered(MouseEvent e) {
			}

			public void mouseExited(MouseEvent e) {
			}
		});

		frame.setContentPane(new JScrollPane(response));
		frame.pack();

		GUIUtils.centerOnScreen(frame);
		frame.setVisible(true);
	}

	/** simply dump status info to the textarea */
	private void sout(final String s) {
		Runnable soutRunner = new Runnable() {
			public void run() {
				if (ttaStatus.getText().equals("")) {
					ttaStatus.setText(s);
				} else {
					ttaStatus.setText(ttaStatus.getText() + "\n" + s);
				}
			}
		};

		if (ThreadUtils.isInEDT()) {
			soutRunner.run();
		} else {
			SwingUtilities.invokeLater(soutRunner);
		}
	}

	private void startTaskAction() {
		try {
			_task.execute();
		} catch (TaskException e) {
			sout(e.getMessage());
		}
	}

	public SampleApp() throws IOException {
		initComponents();
		doInit();
	}

	private void quitProgram() {
		_task.shutdown();
		System.exit(0);
	}

	/** 
	 * The findLocation method finds the longitude and latitude
	 * of the country name passed into it.
	 * @param name - the name of the country
	 * @return an Object ArrayList that contains the country, longitude,and 
	 * latitude
	 */
	private ArrayList<Object> findLocation(String name) {
		ArrayList<String> temp = new ArrayList<String>();
		ArrayList<Object> rc = new ArrayList<Object>();
		Collections.addAll(temp, countries);
		int index = temp.indexOf(name);
		// System.out.println(index);
		rc.add(name);
		for (int i = 0; i < 2; i++) {
			rc.add(location[index][i]);
		}
		return rc;
	}

	/** 
	 * The read method reads from an excel file and grabs the countries and its
	 * longitudes and latitudes and places them in two separate parallel arrays
	 * @throws IOException occurs when file that contains data cannot be opened
	 */
	private void read() throws IOException {
		Workbook w;
		try {
			w = Workbook.getWorkbook(SampleApp.class
					.getResourceAsStream("Countries.xls"));
			Sheet sheet = w.getSheet(0);
			countries = new String[sheet.getRows()];
			location = new double[sheet.getRows()][2];
			for (int j = 0; j < sheet.getRows(); j++) {
				for (int i = 0; i < sheet.getColumns(); i++) {
					if (i == 0 || i == 4 || i == 5) {
						Cell cell = sheet.getCell(i, j);
						if (cell.getType() == CellType.LABEL) {
							countries[j] = cell.getContents();
						}
						if (cell.getType() == CellType.NUMBER) {
							switch (i) {
							case 4:
								location[j][0] = Double.parseDouble(cell
										.getContents());
								break;
							case 5:
								location[j][1] = Double.parseDouble(cell
										.getContents());
								break;
							}
						}
					}

				}
			}
		} catch (BiffException e) {
			sout(e.getMessage());
		}
	}

	/**
	 * The getUserIP method gets the current user's external IP via
	 * reading the stream from the URL
	 * @see <a href="http://stackoverflow.com/questions/2939218/getting-the-external-ip-address-in-java">Stackoverflow</a>
	 * @throws IOException when method cannot get access to site
	 */
	private void getUserIP() throws IOException {
		URL whatismyip = new URL(
				"http://automation.whatismyip.com/n09230945.asp");
		BufferedReader in = new BufferedReader(new InputStreamReader(
				whatismyip.openStream()));

		userIP = in.readLine(); // you get the IP as a String
	}

	/**
	 * The grabUserInfo method grabs the user's relevant info attached to
	 * the external IP by parsing through XML taken from a IP info database
	 * @see <a href="http://www.mkyong.com/java/how-to-read-utf-8-xml-file-in-java-sax-parser/">SAX Parsing</a>
	 * @see <a href="http://ipinfodb.com/ip_location_api.php">IP Database</a>
	 */
	private void grabUserInfo() {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			SAXParser saxParser = factory.newSAXParser();
			userInfo = new ArrayList<String>();

			DefaultHandler handler = new DefaultHandler() {

				boolean countName = false;
				boolean cCode = false;
				boolean rName = false;
				boolean cName = false;
				boolean zCode = false;
				boolean lat = false;
				boolean lon = false;
				boolean tZone = false;

				public void startElement(String uri, String localName,
						String qName, Attributes attributes)
						throws SAXException {

					if (qName.equalsIgnoreCase("countryName")) {
						this.countName = true;
					}

					if (qName.equalsIgnoreCase("countryCode")) {
						this.cCode = true;
					}

					if (qName.equalsIgnoreCase("regionName")) {
						this.rName = true;
					}

					if (qName.equalsIgnoreCase("cityName")) {
						this.cName = true;
					}

					if (qName.equalsIgnoreCase("zipCode")) {
						this.zCode = true;
					}

					if (qName.equalsIgnoreCase("latitude")) {
						this.lat = true;
					}

					if (qName.equalsIgnoreCase("longitude")) {
						this.lon = true;
					}

					if (qName.equalsIgnoreCase("timeZone")) {
						this.tZone = true;
					}

				}

				public void endElement(String uri, String localName,
						String qName) throws SAXException {

				}

				public void characters(char ch[], int start, int length)
						throws SAXException {

					if (countName) {
						userInfo.add(new String(ch, start, length));
						this.countName = false;
					}

					if (cCode) {
						userInfo.add(new String(ch, start, length));
						this.cCode = false;
					}

					if (rName) {
						userInfo.add(new String(ch, start, length));
						this.rName = false;
					}

					if (cName) {
						userInfo.add(new String(ch, start, length));
						this.cName = false;
					}

					if (zCode) {
						userInfo.add(new String(ch, start, length));
						this.zCode = false;
					}

					if (lat) {
						userInfo.add(new String(ch, start, length));
						this.lat = false;
					}

					if (lon) {
						userInfo.add(new String(ch, start, length));
						this.lon = false;
					}

					if (tZone) {
						userInfo.add(new String(ch, start, length));
						this.tZone = false;
					}

				}

			};

			// using my key from http://ipinfodb.com/ip_location_api.php
			URL grabXML = new URL(
					"http://api.ipinfodb.com/v3/ip-city/?key=318f09d6d747b1272ef328b9853777dff1b13bb62be43421641500b3ead3d22a&ip="
							+ userIP + "&format=xml");
			Reader in = new InputStreamReader(grabXML.openStream(), "UTF-8");
			InputSource is = new InputSource(in);
			is.setEncoding("UTF-8");
			saxParser.parse(is, handler);
		} catch (Exception e) {
			sout(e.getMessage());
		}
	}

	/**
	 * RowHeaderRenderer class allows row headers to be customized and formed 
	 * in a JTable
	 * @see <a href="http://www.java2s.com/Code/Java/Swing-Components/TableRowHeaderExample.htm">Table Row Header Example</a>
	 * @version 1.0 11/09/98
	 */
	class RowHeaderRenderer extends JLabel implements ListCellRenderer {
		RowHeaderRenderer(JTable table) {
			JTableHeader header = table.getTableHeader();
			setOpaque(true);
			setBorder(UIManager.getBorder("TableHeader.cellBorder"));
			setHorizontalAlignment(CENTER);
			setForeground(header.getForeground());
			setBackground(header.getBackground());
			setFont(header.getFont());
		}

		public Component getListCellRendererComponent(JList list, Object value,
				int index, boolean isSelected, boolean cellHasFocus) {
			setText((value == null) ? "" : value.toString());
			return this;
		}
	}

	/**
	 * The createTable method creates the tables and customizes the rows' headers
	 * @see RowHeaderRenderer
	 */
	private void createTable() {
		// Grabs the data that will be used to fill the columns
		grabUserInfo();
		
		// A listmodel that contains the row headers
		ListModel lm = new AbstractListModel() {
			String headers[] = { "Country Code: ", "Country Name: ",
					"Region Name: ", "City Name: ", "Zipcode: ", "Latitude: ",
					"Longitude: ", "Timezone: " };

			public int getSize() {
				return headers.length;
			}

			public Object getElementAt(int index) {
				return headers[index];
			}
		};

		DefaultTableModel dm = new DefaultTableModel(lm.getSize(), 1) {
			public boolean isCellEditable(int rowIndex, int mColIndex) {
				return false;
			}
		};
		
		table = new JTable(dm);
		table.setRowHeight(25);
		table.getColumnModel().getColumn(0)
				.setHeaderValue("IP Relevant Data Found");
		
		// Places the user's info into the table
		if (userInfo != null) {
			for (int i = 0; i < userInfo.size(); i++) {
				dm.setValueAt(userInfo.get(i), i, 0);
			}
		}

		list = new JList(lm);
		list.setFixedCellWidth(100);
		list.setFixedCellHeight(table.getRowHeight() + table.getRowMargin()
				- table.getIntercellSpacing().height);
		list.setCellRenderer(new RowHeaderRenderer(table));
		scroll = new JScrollPane(table);
		scroll.setPreferredSize(new Dimension(300, 224));
		scroll.setMaximumSize(new Dimension(300, 224));
		scroll.setMinimumSize(new Dimension(300, 224));
		scroll.setRowHeaderView(list);
	}

	/**
	 * The readWPFile method reads the waypoints.txt file made by the app and
	 * places them into an arraylist.
	 * It is always located in the current directory
	 * @throws IOException when file cannot be created
	 * @see <a href="http://www.roseindia.net/java/beginners/java-read-file-line-by-line.shtml">Read files</a>
	 */
	private void readWPFile() throws IOException {
		wps = new ArrayList<Waypoint>();
		String strLine;
		try {
			fstream = new FileInputStream("waypoints.txt");
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			while ((strLine = br.readLine()) != null) {
				String temp[] = strLine.split(";");
				wps.add(new Waypoint(temp[0], temp[1], temp[2]));
			}
		} catch (Exception ex) {
			createWPFile();
			sout("Created a new waypoints.txt file");
		}
	}

	/**
	 * The createWPFile method creates a new file called waypoints.txt.
	 * Would only be called if it doesn't exist already
	 * @throws IOException 
	 * @see readWPFile
	 */
	private void createWPFile() throws IOException {
		wptext = new File("waypoints.txt");
		if (!wptext.exists()){
			wptext.createNewFile();
		}
	}

	/**
	 * The initWP method initializes the JComboBox that includes all the waypoints
	 * read from the .txt file.
	 * @throws IOException when reading the waypoints file cannot be executed
	 * @see readWPFile
	 */
	private void initWP() throws IOException {
		wayPointsBox.removeAllItems();
		readWPFile();
		for (int i = 0; i < wps.size(); i++) {
			wayPointsBox.addItem(wps.get(i).getName());
		}
		wayPointsBox.insertItemAt("Waypoints...", 0);
		wayPointsBox.setSelectedIndex(0);
	}

	/**
	 * The initComponents method is the main core of this app.
	 * It will intitialize most of the components and add in the 
	 * functionality for most of them
	 * @throws IOException when reading cannot execute
	 */
	private void initComponents() throws IOException {

		// JFormDesigner - Component initialization - DO NOT MODIFY
		// //GEN-BEGIN:initComponents
		// Generated using JFormDesigner non-commercial license
		dialogPane = new JPanel();
		contentPanel = new JPanel();
		zoomSlider = new JSlider(JSlider.HORIZONTAL, ZOOM_MIN, ZOOM_MAX,
				ZOOM_INIT);
		zoomPanel = new JPanel();
		dialogScroll = new JScrollPane();
		jfcSave = new JFileChooser();
		mapContent = new JPanel();
		mPanel1 = new JPanel();
		mapOptions = new JPanel();
		jcbox = new JComboBox<String>();
		loc = new ArrayList<Object>();
		save = new JButton();
		saveWP = new JButton("Save Waypoint");
		WPPanel = new JPanel();
		nameText = new JTextField();
		nameLabel = new JLabel();
		btnup = new JButton("UP");
		btndown = new JButton("DOWN");
		btnleft = new JButton("LEFT");
		btnright = new JButton("RIGHT");
		panning = new JPanel();
		wayPointsBox = new JComboBox<String>();
		ipLabel = new JLabel();
		ipText = new JTextField();
		userIP = new String();
		panel1 = new JPanel();
		label2 = new JLabel();
		ttfSizeW = new JTextField();
		label4 = new JLabel();
		ttfLat = new JTextField();
		btnGetMap = new JButton();
		label3 = new JLabel();
		ttfSizeH = new JTextField();
		label5 = new JLabel();
		ttfLon = new JTextField();
		btnQuit = new JButton();
		label1 = new JLabel();
		// ttfLicense = new JTextField();
		label6 = new JLabel();
		ttfZoom = new JTextField();
		scrollPane1 = new JScrollPane();
		ttaStatus = new JTextArea();
		panel2 = new JPanel();
		panel3 = new JPanel();
		checkboxRecvStatus = new JCheckBox();
		checkboxSendStatus = new JCheckBox();
		ttfProgressMsg = new JTextField();
		progressBar = new JProgressBar();
		lblProgressStatus = new JLabel();

		try {
			this.read();
		} catch (IOException e) {
			sout(e.getMessage());
		}

		// ======== this ========
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setTitle("Google Static Maps");
		setIconImage(null);
		Container contentPane = getContentPane();
		contentPane.setLayout(new BorderLayout());

		// ======== dialogPane ========
		{

			dialogPane.setBorder(new EmptyBorder(12, 12, 12, 12));
			dialogPane.setOpaque(false);
			dialogPane.setLayout(new BoxLayout(dialogPane, BoxLayout.X_AXIS));
			// ======== contentPanel ========
			{
				contentPanel.setOpaque(false);
				contentPanel.setLayout(new TableLayout(new double[][] {
						{ TableLayout.FILL },
						{ TableLayout.PREFERRED, TableLayout.FILL,
								TableLayout.PREFERRED } }));
				((TableLayout) contentPanel.getLayout()).setHGap(5);
				((TableLayout) contentPanel.getLayout()).setVGap(5);

				// ======== mapContent ========
				mapContent.setLayout(new GridLayout(1, 0));
				mapContent.setMaximumSize(new Dimension(1187, 600));


				// ======== panel1 ========
				{
					panel1.setOpaque(false);
					panel1.setBorder(new CompoundBorder(new TitledBorder(
							"Configure the inputs to Google Static Maps"),
							Borders.DLU2_BORDER));
					panel1.setLayout(new TableLayout(new double[][] {
							{ 0.17, 0.3, 0.0, 0.3, 0.0, TableLayout.FILL },
							{ TableLayout.PREFERRED, TableLayout.PREFERRED,
									TableLayout.PREFERRED } }));
					((TableLayout) panel1.getLayout()).setHGap(5);
					((TableLayout) panel1.getLayout()).setVGap(5);

					// ======== mPanel1 ========
					mPanel1.setOpaque(false);
					mPanel1.setLayout(new GridBagLayout());
					mPanel1.setBorder(new CompoundBorder(new TitledBorder(
							"Location"), Borders.DLU2_BORDER));

					// ======== mapOptions ========
					mapOptions.setOpaque(false);
					mapOptions.setBorder(new CompoundBorder(new TitledBorder(
							"Map Options"), Borders.DLU2_BORDER));
					mapOptions.setLayout(new BorderLayout());

					try {
						getUserIP();
						ipText.setText(userIP);
					} catch (IOException ex) {
						sout(ex.getMessage());
						ipText.setText("Cannot get IP");
					}
					
					// ======== Boxes ========
					Box IPBox = Box.createHorizontalBox(), InfoBox = Box
							.createHorizontalBox(), NameBox = Box
							.createHorizontalBox(), WPBox = Box
							.createHorizontalBox(), PanningBox = Box
							.createHorizontalBox(), zoomBox = Box
							.createHorizontalBox(), verticalBox = Box
							.createVerticalBox();

					// ======== IPBox ========
					ipText.setColumns(16);
					ipText.setEditable(false);
					ipText.setMaximumSize(new Dimension(190, 25));
					ipLabel.setText("Current User's IP: ");
					IPBox.add(ipLabel);
					IPBox.add(Box.createHorizontalStrut(5));
					IPBox.add(ipText);
					
					// ======== InfoBox ========
					createTable();
					InfoBox.add(scroll);
					
					// ======== WPBox ========
					nameLabel.setText("Waypoint Name: ");
					nameText.setColumns(16);
					nameText.setMaximumSize(new Dimension(190, 25));
					NameBox.add(nameLabel);
					NameBox.add(Box.createHorizontalStrut(5));
					NameBox.add(nameText);
					WPPanel.setLayout(new TableLayout(new double[][] {
							{ TableLayout.PREFERRED, 0.05, TableLayout.FILL },
							{ TableLayout.FILL }

					}));
					readWPFile();
					initWP();
					wayPointsBox.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							JComboBox cb = (JComboBox) e.getSource();
							String temp = (String) cb.getSelectedItem();
							if (wayPointsBox.getSelectedIndex() > 0) {

								for (Waypoint i : wps) {
									if (temp.equals(i.getName())) {
										ttfLat.setText(i.getLat());
										ttfLon.setText(i.getLong());
									}
								}
							}
						}
					});
					saveWP.setMnemonic('W');
					saveWP.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							String name = new String(nameText.getText().trim());
							String lon = new String(ttfLon.getText().trim());
							String lat = new String(ttfLat.getText().trim());
							// from:
							// http://www.codingforums.com/showthread.php?t=108925
							try {
								if (!name.isEmpty()) {
									BufferedWriter fileOut = new BufferedWriter(
											new FileWriter("waypoints.txt",
													true));
									fileOut.append(name + ";" + lon + ";" + lat
											+ "\n");
									fileOut.close();
									nameText.setText(null);
									initWP();
								} else {
									JOptionPane.showMessageDialog(null,
											"Enter a name...", "Error!",
											JOptionPane.INFORMATION_MESSAGE);
								}
							} catch (IOException ex) {
								sout(ex.getMessage());
							}
						}
					});
					WPPanel.add(saveWP, "0, 0");
					WPPanel.add(wayPointsBox, "2, 0");
					WPBox.add(WPPanel);
					WPBox.setMaximumSize(new Dimension(300, 25));

					// ======== PanningBox ========
					panning.setLayout(new TableLayout(new double[][] {
							{ TableLayout.FILL, TableLayout.FILL,
									TableLayout.FILL },
							{ TableLayout.FILL, TableLayout.FILL,
									TableLayout.FILL } }));
					
					PanningActionBtn btnHandler = new PanningActionBtn();
					btnup.addActionListener(btnHandler);
					btnleft.addActionListener(btnHandler);
					btnright.addActionListener(btnHandler);
					btndown.addActionListener(btnHandler);

					PanningActionKey keyHandler = new PanningActionKey();
					btnup.addKeyListener(keyHandler);
					btnleft.addKeyListener(keyHandler);
					btnright.addKeyListener(keyHandler);
					btndown.addKeyListener(keyHandler);

					panning.add(btnup, "1, 0");
					panning.add(btnleft, "0, 1");
					panning.add(btnright, "2, 1");
					panning.add(btndown, "1, 2");
					panning.setBorder(new CompoundBorder(
							new TitledBorder("Pan"), Borders.DLU2_BORDER));
					PanningBox.add(panning);
					PanningBox.setMaximumSize(new Dimension(300, 100));

					// ======== ZoomBox ========
					zoomSlider.setMajorTickSpacing(2);
					zoomSlider.setSize(500, 25);
					zoomSlider.setPreferredSize(new Dimension(290, 30));
					zoomSlider.setPaintTicks(true);
					zoomSlider.addChangeListener(new ChangeListener() {
						public void stateChanged(ChangeEvent e) {
							int zoom = ((JSlider) e.getSource()).getValue();
							ttfZoom.setText(String.format("%d", zoom));
							startTaskAction();
						}
					});
					zoomSlider.addMouseWheelListener(new MouseWheelListener() {
						@Override
						public void mouseWheelMoved(MouseWheelEvent e) {
							if (e.getWheelRotation() < 0)
								zoomSlider.setValue(zoomSlider.getValue() - 1);
							else
								zoomSlider.setValue(zoomSlider.getValue() + 1);
						}

					});
					zoomPanel.setBorder(new CompoundBorder(new TitledBorder(
							"Zoom"), Borders.DLU2_BORDER));
					zoomPanel.setMaximumSize(new Dimension(300, 100));
					zoomPanel.add(zoomSlider);
					zoomBox.add(zoomPanel);

					// ======== verticalBox ========
					verticalBox.add(IPBox);
					verticalBox.add(Box.createVerticalStrut(10));
					verticalBox.add(InfoBox);
					verticalBox.add(Box.createVerticalStrut(5));
					verticalBox.add(NameBox);
					verticalBox.add(Box.createVerticalStrut(10));
					verticalBox.add(WPBox);
					verticalBox.add(Box.createVerticalStrut(5));
					verticalBox.add(PanningBox);
					verticalBox.add(Box.createVerticalStrut(10));
					verticalBox.add(zoomBox);

					mapOptions.add(verticalBox, BorderLayout.CENTER);

					// ---- label2 ----
					label2.setText("Size Width");
					label2.setHorizontalAlignment(SwingConstants.RIGHT);
					panel1.add(label2, new TableLayoutConstraints(0, 0, 0, 0,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- ttfSizeW ----
					ttfSizeW.setText("512");
					panel1.add(ttfSizeW, new TableLayoutConstraints(1, 0, 1, 0,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- label4 ----
					label4.setText("Latitude");
					label4.setHorizontalAlignment(SwingConstants.RIGHT);
					panel1.add(label4, new TableLayoutConstraints(2, 0, 2, 0,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- ttfLat ----
					ttfLat.setText("38.931099");
					panel1.add(ttfLat, new TableLayoutConstraints(3, 0, 3, 0,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));



					// ---- btnGetMap ----
					btnGetMap.setText("Get Map");
					btnGetMap.setHorizontalAlignment(SwingConstants.LEFT);
					btnGetMap.setMnemonic('G');
					btnGetMap.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							startTaskAction();
						}
					});
					panel1.add(btnGetMap, new TableLayoutConstraints(5, 0, 5,
							0, TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- label3 ----
					label3.setText("Size Height");
					label3.setHorizontalAlignment(SwingConstants.RIGHT);
					panel1.add(label3, new TableLayoutConstraints(0, 1, 0, 1,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- ttfSizeH ----
					ttfSizeH.setText("512");
					panel1.add(ttfSizeH, new TableLayoutConstraints(1, 1, 1, 1,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- label5 ----
					label5.setText("Longitude");
					label5.setHorizontalAlignment(SwingConstants.RIGHT);
					panel1.add(label5, new TableLayoutConstraints(2, 1, 2, 1,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- ttfLon ----
					ttfLon.setText("-77.3489");
					panel1.add(ttfLon, new TableLayoutConstraints(3, 1, 3, 1,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- btnQuit ----
					btnQuit.setText("Quit");
					btnQuit.setMnemonic('Q');
					btnQuit.setHorizontalAlignment(SwingConstants.LEFT);
					btnQuit.setHorizontalTextPosition(SwingConstants.RIGHT);
					btnQuit.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							quitProgram();
						}
					});
					panel1.add(btnQuit, new TableLayoutConstraints(5, 1, 5, 1,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- save ----
					save.setText("Save Map Image");
					save.setHorizontalAlignment(SwingConstants.LEFT);
					save.setMnemonic('S');
					save.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							if (_img != null) {
								// from:
								// http://docs.oracle.com/javase/tutorial/uiswing/components/filechooser.html
								File saveFile = new File("map.png");
								jfcSave.setSelectedFile(saveFile);
								int returnValue = jfcSave
										.showSaveDialog(SampleApp.this);
								if (returnValue == JFileChooser.APPROVE_OPTION) {
									saveFile = jfcSave.getSelectedFile();
									try {
										ImageIO.write(_img, "png", saveFile);
									}

									catch (IOException ex) {
										sout(ex.getMessage());
									}
								}
							}
						}
					});
					panel1.add(save, new TableLayoutConstraints(5, 2, 5, 2,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- label1 ----
					label1.setText("Countries");
					label1.setHorizontalAlignment(SwingConstants.RIGHT);
					panel1.add(label1, new TableLayoutConstraints(0, 2, 0, 2,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));
					
					// ---- jcbox ----
					for (String s : countries) {
						jcbox.addItem(s);
					}
					jcbox.setSelectedIndex(0);
					jcbox.insertItemAt("Select a country...", 0);
					jcbox.removeItemAt(1);
					jcbox.setToolTipText("Enter your own URI for a file to download in the background");
					jcbox.addActionListener(new ActionListener() {
						public void actionPerformed(ActionEvent e) {
							if (jcbox.getSelectedIndex() > 0) {
								JComboBox<String> cb = (JComboBox<String>)e.getSource();
								String country = (String) cb.getSelectedItem();
								loc = findLocation(country);
								ttfLat.setText(((Double) loc.get(1)).toString());
								ttfLon.setText(((Double) loc.get(2)).toString());
							}
						}
					});
					panel1.add(jcbox, new TableLayoutConstraints(1, 2, 1, 2,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));
					
					// ---- label6 ----
					label6.setText("Zoom");
					label6.setHorizontalAlignment(SwingConstants.RIGHT);
					panel1.add(label6, new TableLayoutConstraints(2, 2, 2, 2,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- ttfZoom ----
					ttfZoom.setText("14");
					panel1.add(ttfZoom, new TableLayoutConstraints(3, 2, 3, 2,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

				}
				contentPanel.add(panel1, new TableLayoutConstraints(0, 0, 0, 0,
						TableLayoutConstraints.FULL,
						TableLayoutConstraints.FULL));
				mPanel1.setMaximumSize(new Dimension(550, 600));
				mPanel1.setPreferredSize(new Dimension(550, 600));
				mPanel1.setMinimumSize(new Dimension(550, 600));
				mapOptions.setPreferredSize(new Dimension(350, 600));
				mapOptions.setMinimumSize(new Dimension(350, 600));
				mapOptions.setMaximumSize(new Dimension(350, 600));
				mapContent.add(mPanel1);
				mapContent.add(mapOptions);

				// ======== scrollPane1 ========
				{
					scrollPane1
							.setBorder(new TitledBorder(
									"System.out - displays all status and progress messages, etc."));
					scrollPane1.setOpaque(false);

					// ---- ttaStatus ----
					ttaStatus.setBorder(Borders
							.createEmptyBorder("1dlu, 1dlu, 1dlu, 1dlu"));
					ttaStatus
							.setToolTipText("<html>Task progress updates (messages) are displayed here,<br>along with any other output generated by the Task.<html>");
					scrollPane1.setViewportView(ttaStatus);
				}
				contentPanel.add(scrollPane1, new TableLayoutConstraints(0, 1,
						0, 1, TableLayoutConstraints.FULL,
						TableLayoutConstraints.FULL));

				// ======== panel2 ========
				{
					panel2.setOpaque(false);
					panel2.setBorder(new CompoundBorder(new TitledBorder(
							"Status - control progress reporting"),
							Borders.DLU2_BORDER));
					panel2.setLayout(new TableLayout(new double[][] {
							{ 0.45, TableLayout.FILL, 0.45 },
							{ TableLayout.PREFERRED, TableLayout.PREFERRED } }));
					((TableLayout) panel2.getLayout()).setHGap(5);
					((TableLayout) panel2.getLayout()).setVGap(5);

					// ======== panel3 ========
					{
						panel3.setOpaque(false);
						panel3.setLayout(new GridLayout(1, 2));

						// ---- checkboxRecvStatus ----
						checkboxRecvStatus.setText("Enable \"Recieve\"");
						checkboxRecvStatus.setOpaque(false);
						checkboxRecvStatus
								.setToolTipText("Task will fire \"send\" status updates");
						checkboxRecvStatus.setSelected(true);
						panel3.add(checkboxRecvStatus);

						// ---- checkboxSendStatus ----
						checkboxSendStatus.setText("Enable \"Send\"");
						checkboxSendStatus.setOpaque(false);
						checkboxSendStatus
								.setToolTipText("Task will fire \"recieve\" status updates");
						panel3.add(checkboxSendStatus);
					}
					panel2.add(panel3, new TableLayoutConstraints(0, 0, 0, 0,
							TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- ttfProgressMsg ----
					ttfProgressMsg
							.setText("Loading map from Google Static Maps");
					ttfProgressMsg
							.setToolTipText("Set the task progress message here");
					panel2.add(ttfProgressMsg, new TableLayoutConstraints(2, 0,
							2, 0, TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- progressBar ----
					progressBar.setStringPainted(true);
					progressBar.setString("progress %");
					progressBar.setToolTipText("% progress is displayed here");
					panel2.add(progressBar, new TableLayoutConstraints(0, 1, 0,
							1, TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));

					// ---- lblProgressStatus ----
					lblProgressStatus.setText("task status listener");
					lblProgressStatus
							.setHorizontalTextPosition(SwingConstants.LEFT);
					lblProgressStatus
							.setHorizontalAlignment(SwingConstants.LEFT);
					lblProgressStatus
							.setToolTipText("Task status messages are displayed here when the task runs");
					panel2.add(lblProgressStatus, new TableLayoutConstraints(2,
							1, 2, 1, TableLayoutConstraints.FULL,
							TableLayoutConstraints.FULL));
				}
				contentPanel.add(panel2, new TableLayoutConstraints(0, 2, 0, 2,
						TableLayoutConstraints.FULL,
						TableLayoutConstraints.FULL));
			}
			contentPanel.setMinimumSize(new Dimension(550, 600));
			contentPanel.setMaximumSize(new Dimension(550, 600));
			contentPanel.setPreferredSize(new Dimension(550, 600));
			
			// ======== dialogPane ========
			dialogPane.add(contentPanel, BorderLayout.WEST);
			dialogPane.add(mapContent, BorderLayout.CENTER);
			dialogPane.add(mapOptions, BorderLayout.EAST);
		}
		// adding a scroll to dialog panel for users with low screen resolution
		dialogScroll.setViewportView(dialogPane);
		contentPane.add(dialogScroll, BorderLayout.CENTER);
		setSize(580, 680);
		setLocationRelativeTo(null);
		// JFormDesigner - End of component initialization
		// //GEN-END:initComponents
	}

	/**
	 * Class Waypoint is used as a container for the country name,
	 * longitude and latitude. I created it because HashMaps and Maps
	 * would make the code more complex than it needs to be
	 * @author Neil Brian Guzman
	 */
	class Waypoint {
		private String name; // stores the name
		private String lon;  // stores the longitude
		private String lat;  // stores the latitude
		
		/**
		 * Initializes the country name, longitude, and latitude if passed in as empty
		 */
		Waypoint() {
			this(null, null, null);
		}
		
		/**
		 * Waypoint constructor initializes the country name, longitude, and latitude
		 * @param s name of country
		 * @param a latitude of country
		 * @param o longitude of country
		 */
		Waypoint(String s, String a, String o) {
			name = s;
			lon = o;
			lat = a;
		}

		/**
		 * The getName method gets the name of the country
		 * @return country name
		 */
		private String getName() {
			return name;
		}
		
		/**
		 * The getLat method gets the latitude of the country
		 * @return latitude
		 */
		private String getLat() {
			return lat;
		}
		
		/**
		 * The getLong method gets the longitude of the country
		 * @return longitude
		 */
		private String getLong() {
			return lon;
		}

		/**
		 * The equals method checks returns true if 2 waypoint objects are the same
		 * @return true if both waypoint objects are the same
		 */
		public boolean equals(Object z) {
			boolean rc = false;
			if (z instanceof Waypoint) {
				Waypoint temp = (Waypoint) z;
				if (temp.getName().equals(getName())
						&& temp.getLat().equals(getLat())
						&& temp.getLong().equals(getLong())) {
					rc = true;
				}

			}
			return rc;
		}
	}

	/**
	 * A named inner class that handles the panning buttons' actions
	 * @author Neil Brian Guzman
	 * @author Husain Fazal for the formula and UP code
	 */
	class PanningActionBtn implements ActionListener {

		@Override
		public void actionPerformed(ActionEvent e) {
			// credits to Husain Fazal for the formula and partial fragments of the code
			int zoom = Integer.parseInt(ttfZoom.getText());
			double toadd = 131.072 / java.lang.Math.pow(2, zoom + 1);
			
			if (e.getSource() == btnup) {
				if ((Double.parseDouble(ttfLat.getText()) + toadd) > 85) {
					ttfLat.setText(Double.toString(Double.parseDouble(ttfLat
							.getText()) + toadd - 170));
				} else {
					ttfLat.setText(Double.toString(Double.parseDouble(ttfLat
							.getText()) + toadd));
				}
			} else if (e.getSource() == btndown) {
				if ((Double.parseDouble(ttfLat.getText()) - toadd) < -85) {
					ttfLat.setText(Double.toString(Double.parseDouble(ttfLat
							.getText()) - toadd + 170));
				} else {
					ttfLat.setText(Double.toString(Double.parseDouble(ttfLat
							.getText()) - toadd));
				}
			} else if (e.getSource() == btnleft) {
				if ((Double.parseDouble(ttfLon.getText()) - toadd) < -180) {
					ttfLon.setText(Double.toString(Double.parseDouble(ttfLon
							.getText()) - toadd + 360));
				} else {
					ttfLon.setText(Double.toString(Double.parseDouble(ttfLon
							.getText()) - toadd));
				}
			} else if (e.getSource() == btnright) {
				if ((Double.parseDouble(ttfLon.getText()) + toadd) > 180) {
					ttfLon.setText(Double.toString(Double.parseDouble(ttfLon
							.getText()) + toadd - 360));
				} else {
					ttfLon.setText(Double.toString(Double.parseDouble(ttfLon
							.getText()) + toadd));
				}
			}
			startTaskAction();
		}
	}
	
	/**
	 * A named inner class that handles the panning keys' actions
	 * @author Neil Brian Guzman
	 * @author Husain Fazal for the formula and UP code
	 */
	class PanningActionKey implements KeyListener {

		@Override
		public void keyPressed(KeyEvent e) {
			// credits to Husain Fazal for the formula and partial fragments of the code
			int zoom = Integer.parseInt(ttfZoom.getText());
			double toadd = 131.072 / java.lang.Math.pow(2, zoom + 1);

			int keyCode = e.getKeyCode();
			switch (keyCode) {
			case KeyEvent.VK_LEFT:
				if ((Double.parseDouble(ttfLon.getText()) - toadd) < -180) {
					ttfLon.setText(Double.toString(Double.parseDouble(ttfLon
							.getText()) - toadd + 360));
				} else {
					ttfLon.setText(Double.toString(Double.parseDouble(ttfLon
							.getText()) - toadd));
				}
				break;
			case KeyEvent.VK_RIGHT:
				if ((Double.parseDouble(ttfLon.getText()) + toadd) > 180) {
					ttfLon.setText(Double.toString(Double.parseDouble(ttfLon
							.getText()) + toadd - 360));
				} else {
					ttfLon.setText(Double.toString(Double.parseDouble(ttfLon
							.getText()) + toadd));
				}
				break;
			case KeyEvent.VK_UP:
				if ((Double.parseDouble(ttfLat.getText()) + toadd) > 85) {
					ttfLat.setText(Double.toString(Double.parseDouble(ttfLat
							.getText()) + toadd - 170));
				} else {
					ttfLat.setText(Double.toString(Double.parseDouble(ttfLat
							.getText()) + toadd));
				}
				break;
			case KeyEvent.VK_DOWN:
				if ((Double.parseDouble(ttfLat.getText()) - toadd) < -85) {
					ttfLat.setText(Double.toString(Double.parseDouble(ttfLat
							.getText()) - toadd + 170));
				} else {
					ttfLat.setText(Double.toString(Double.parseDouble(ttfLat
							.getText()) - toadd));
				}
				break;
			}
		}

		@Override
		public void keyReleased(KeyEvent arg0) {
			// TODO Auto-generated method stub

		}

		@Override
		public void keyTyped(KeyEvent arg0) {
			// TODO Auto-generated method stub

		}

	}
	// JFormDesigner - Variables declaration - DO NOT MODIFY
	// //GEN-BEGIN:variables
	// Generated using JFormDesigner non-commercial license
	private JFileChooser jfcSave; // to show save dialog box and choose directory
	static final int ZOOM_MIN = 0; // defines min of zoom bar
	static final int ZOOM_MAX = 19; // defines max of zoom bar
	static final int ZOOM_INIT = 14; // defines initial start of zoom bar
	private JSlider zoomSlider; // zoom jslider
	private JPanel zoomPanel; // panel to hold zoomslider
	private JScrollPane dialogScroll; // scrollpane to show scroll for dialogpane
	private JComboBox<String> wayPointsBox; // combobox of waypoints
	private ArrayList<Waypoint> wps; // arraylist of Waypoint objects
	private File wptext; // wp .txt file
	private FileInputStream fstream; // file input stream
	private JButton saveWP; // button to save waypoint
	private JPanel WPPanel; // panel to hold waypoint components
	private JTextField nameText; // textfield to hold name of waypoint
	private JLabel nameLabel; // label to show "Waypoint Name: "
	private JTable table; // table to hold table of user data
	private JList list; // list to hold row headers
	private JScrollPane scroll; // scrollpane for table if it ever gets bigger
	private ArrayList<String> userInfo; // arraylist to hold user's ip relevant info
	private JPanel panning; // panel to hold panning components
	private JButton btnup; // btn to pan up
	private JButton btndown; // btn to pan down
	private JButton btnleft; // btn to pan left
	private JButton btnright; // btn to pan right
	private static String[] countries; // array of country names
	private static double[][] location; // 2d-array of country locations
	private static ArrayList<Object> loc; // arraylist of country, longitude, and latitude
	private JPanel mapContent; // panel to hold map contents
	private JPanel mPanel1; // panel to hold map contents
	private JPanel mapOptions; // panel to hold map options
	
	@SuppressWarnings(RAWTYPES)
	private JComboBox<String> jcbox; // combobox with country listing
	private JButton save; // button to save map
	private JLabel ipLabel; // label to show "Current User's IP"
	private JTextField ipText; // textfield to display uneditable user's external IP
	private String userIP; // hold's the users IP
	
	// ACTUAL JFORMDESIGNER STUFF BELOW
	private JPanel dialogPane;
	private JPanel contentPanel;
	private JPanel panel1; 
	private JLabel label2;
	private JTextField ttfSizeW;
	private JLabel label4;
	private JTextField ttfLat;
	private JButton btnGetMap;
	private JLabel label3;
	private JTextField ttfSizeH;
	private JLabel label5;
	private JTextField ttfLon;
	private JButton btnQuit;
	private JLabel label1;
	private JLabel label6;
	private JTextField ttfZoom;
	private JScrollPane scrollPane1;
	private JTextArea ttaStatus;
	private JPanel panel2;
	private JPanel panel3;
	private JCheckBox checkboxRecvStatus;
	private JCheckBox checkboxSendStatus;
	private JTextField ttfProgressMsg;
	private JProgressBar progressBar;
	private JLabel lblProgressStatus;
	// JFormDesigner - End of variables declaration //GEN-END:variables
}
