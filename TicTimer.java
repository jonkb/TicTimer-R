/**Main thread for the TicTimer program
 * Sets up the main window, including the functionality of its buttons.
 */
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import java.io.*;
import java.util.*;
import gnu.io.*;//From RXTX

public class TicTimer extends Thread implements KeyListener {
    static TicTimer tic_session;
    
    // main window containers, buttons, labels, etc
    static JFrame main_frame = new JFrame("Tic Timer");
    
    static JPanel main_panel = new JPanel();
    
    static ClockView clock_panel = new ClockView();
    static SessionWatch session_time_panel = new SessionWatch();
    static TicWatch tic_time_panel = new TicWatch();
    static JLabel session_start_label = new JLabel("Session Start Time:  00:00:00");
    static JPanel session_status_panel = new JPanel();
    static JPanel button_panel = new JPanel();
    static JLabel session_status_label = new JLabel();
    static JLabel reward_notification_label = new JLabel();
    static JLabel tic_counter_version_label = new JLabel("program version 1.0, Aug 6, 2010");
    static JButton setup_button = new JButton("Setup");
    static JButton session_button = new JButton("Start Session");
    static JButton end_button = new JButton("End Session");
    static JButton tic_button = new JButton("Tic Detected");
    static JTextArea progress_area = new JTextArea();
    static JScrollPane progressscroll = new JScrollPane(progress_area);
    static JFileChooser chooser = new JFileChooser();
    
    //Used by COM link
    final static int TIMEOUT = 2000;
    final static String appName = "TicTimer_test";
    final static byte RM_BUTTON = 0;
    final static byte RM_LINK = 1;
    static Scanner user_in = new Scanner(System.in);
    static byte reward_mode = RM_LINK;
    static SerialPort serialPort = null;
    static OutputStream serialStream = null;
    
    static File NCRSource;
    static File log;
    static Scanner readSource;
    static PrintStream log_stream;
    static int exit_button;
    static int end_button_press;
    static boolean session_running = false;
    static LinkedList<Timestamp> ncrTimes = new LinkedList<Timestamp>();
    
    // setup info variables
    static String patid = "";
    static String session_type;
    static int session_number = 1;
    
    // session timing variables
    static Double d_int = new Double(0);
    static int total_time;
    static Double running_time = new Double(0);
    
    public void setup_main_window(){
        // setup main window
        main_frame.setSize(500,500);
        main_frame.setResizable(false);
        main_frame.getContentPane().setLayout( new FlowLayout(FlowLayout.CENTER,10,10) );
        main_frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        
        // setup border for panels
        Border etched_border;
        etched_border = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
        
        // setup main panels
        main_panel.setPreferredSize(new Dimension(450,450));
        Thread clock_thread = new Thread(clock_panel);
        clock_thread.start();
        clock_panel.setPreferredSize(new Dimension(400,30));
        main_panel.add(clock_panel);
        main_panel.add(session_start_label);
        session_status_panel.setBackground(Color.RED);
        session_status_panel.setPreferredSize(new Dimension(400,30));
        session_status_label.setText("Session status: stopped");
        session_status_panel.add(session_status_label);
        session_time_panel.reset();
        tic_time_panel.reset();
        
        //add Listener to main frame
        main_frame.addKeyListener(this);
        
        // add buttons
        button_panel.setPreferredSize(new Dimension(430,40));
        ActionListener setupL = new ActionListener(){
            public void actionPerformed(ActionEvent e){
                //Setup can fail now
                if(setup())
                    session_button.setEnabled(true);
            }
        };
        setup_button.addActionListener(setupL);
        setup_button.setToolTipText("Press to do setup steps");
        button_panel.add(setup_button);
        
        ActionListener sessionL = new ActionListener(){
            public void actionPerformed(ActionEvent e){
                try {
                    if(log.exists()){
                        String message = "The current log file already exists. If you continue, you will append to that log file";
                        message += "\nIf this is a DRZ run to be used later for NCR, it could cause errors";
                        message += "\nTo avoid modifying the existing file, go back to setup and choose a new filename";
                        JOptionPane.showMessageDialog(main_frame, message, "WARNING: Appending",JOptionPane.WARNING_MESSAGE);
                    }
                    //true means append
                    log_stream = new PrintStream(new FileOutputStream(log, true));
                } catch (Exception ex) {}
                session_running = true;
                Session session = new Session();
                session.start();
                setup_button.setEnabled(false);
                session_button.setEnabled(false);
                end_button.setEnabled(true);
                tic_button.setEnabled(true);
            }
        };
        session_button.addActionListener(sessionL);
        session_button.setToolTipText("Press to start the session");
        session_button.setEnabled(false);
        button_panel.add(session_button);
        
        ActionListener endL = new ActionListener(){
            public void actionPerformed(ActionEvent e){
                end_button_press = JOptionPane.showConfirmDialog(main_frame,"Are you sure you want to end the session early?","End early?",JOptionPane.OK_CANCEL_OPTION);
                if ( end_button_press == JOptionPane.OK_OPTION ) {
                    JOptionPane.showMessageDialog(main_frame,"OK, ending now","Ending early",JOptionPane.WARNING_MESSAGE);
                    endSession();
                    session_status_label.setText("Session status: ended early");
                }
            }
        };
        end_button.setEnabled(false);
        end_button.addActionListener(endL);
        end_button.setToolTipText("Press to end the session");
        button_panel.add(end_button);
        
        ActionListener ticL = new ActionListener(){
            public void actionPerformed(ActionEvent e){
                // tic detected
                tic_detected();
            }
        };
        tic_button.setEnabled(false);
        tic_button.addActionListener(ticL);
        tic_button.setToolTipText("Press when you detect a tic");
        button_panel.add(tic_button);
        
        //Add Label for reward notification
        reward_notification_label.setBackground(Color.WHITE);
        reward_notification_label.setPreferredSize(new Dimension(400,30));
        reward_notification_label.setText("");
        reward_notification_label.setOpaque(true);
        
        // construct progress text area
        progressscroll.setPreferredSize(new Dimension(300,150));
        progressscroll.setBorder(etched_border);
        progress_area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        progress_area.setEditable(false);
        
        // construct window from parts
        main_panel.add(session_status_panel);
        main_panel.add(session_time_panel);
        main_panel.add(button_panel);
        main_panel.add(reward_notification_label);
        main_panel.add(tic_time_panel);
        main_panel.add(progressscroll);
        main_frame.add(main_panel);
        WindowListener mainL = new WindowAdapter(){
            public void windowClosing(WindowEvent e){
                if ( session_running ) {
                    exit_button = JOptionPane.showConfirmDialog(main_frame,"Are you sure you want to exit?","Exit?",JOptionPane.OK_CANCEL_OPTION);
                    if(exit_button == JOptionPane.CANCEL_OPTION){
                        return;
                    }
                }
                //Close COM port and stream
                if(reward_mode == RM_LINK){
                    serialPort.close();
                    try{
                        serialStream.close();
                    }
                    catch(Exception e1){
                        System.out.println("Exception: "+e1.toString());
                    }
                }
                //Close the window and the program
                main_frame.dispose();
                System.exit(0);
            }
        };
        main_frame.addWindowListener(mainL);
        
        main_frame.setVisible(true);
        
    }
    
    public static boolean setup(){
        JLabel q1 = new JLabel("Enter subject number: ");
        JLabel q7 = new JLabel("Enter the total time of the session(min): ");
        JLabel q8 = new JLabel("Enter the session number");
        JLabel q10 = new JLabel("What type of session is this?");
        String title1 = "Enter subject ID";
        String title7 = "Enter session time";
        String title8 = "Enter session number";
        String title10 = "Enter session type";
        Object a1 = new Object();
        Object a7 = new Object();
        Object a8 = new Object();
        Object a10 = new Object();
        Object[] possibleValues = { "baseline", "verbal", "DRZ", "NCR" };
        int a9 = 0;
        
        while( true ){
            a1 = JOptionPane.showInputDialog(main_frame,q1,title1,JOptionPane.QUESTION_MESSAGE, null, null, patid);
            try {
                if(a1 == null)
                    return false;
                patid = a1.toString();
            } catch (Exception e) {}
            if ( patid.length() > 2 ) break;
        }
        
        while( true ){
            a7 = JOptionPane.showInputDialog(main_frame,q7,title7,JOptionPane.QUESTION_MESSAGE,null,null,5);
            try {
                if(a7 == null)
                    return false;
                total_time = 60 * Integer.parseInt(a7.toString());
            } catch (Exception e) {}
            if ( total_time > 0 && total_time <= 30 * 60 ) break;
        }
        
        while( true ){
            a8 = JOptionPane.showInputDialog(main_frame,q8,title8,JOptionPane.QUESTION_MESSAGE,null,null,"1");
            try {
                if(a8 == null)
                    return false;
                session_number = Integer.parseInt(a8.toString());
            } catch (Exception e) {}
            if ( session_number > 0 && session_number <= 10 ) break;
        }
        
        while( true ){
            a10 = JOptionPane.showInputDialog(main_frame,q10,title10,JOptionPane.INFORMATION_MESSAGE,null,possibleValues,possibleValues[0]);
            try {
                if(a10 == null)
                    return false;
                session_type = a10.toString();
            } catch (Exception e) {}
            if ( session_type.length() > 2 ) break;
        }
        
        //Choose DRZ file from which to send the rewards
        if(session_type.equals("NCR")){ //if NCR
            chooser.setDialogTitle("Choose a DRZ file");
            chooser.setSelectedFile(new File(System.getProperty("user.dir"), patid + "_session*" + "_" + possibleValues[2] + "_TicTimer_log.txt"));
            while ( true ){
                try {
                    a9 = chooser.showOpenDialog(main_frame);
                } catch (Exception e) {}
                if ( a9 == JFileChooser.CANCEL_OPTION ) return false;
                if ( a9 == JFileChooser.APPROVE_OPTION ) {
                    NCRSource = chooser.getSelectedFile();
                    try{
                        readSource = new Scanner(NCRSource);
                        if(readSource.hasNextLine())
                            break;
                    } catch (FileNotFoundException e) {}
                    //Retry if the file didn't exist or was empty
                }
            }
            
            while(readSource.hasNextLine()) {
                String line = readSource.nextLine();
                if(line.length() > 31 && line.substring(0, 31).equals("10s tic free interval ended at ")){
                    try {
                        int h = Integer.parseInt(line.substring(31, 33));
                        int m = Integer.parseInt(line.substring(34, 36));
                        int s = Integer.parseInt(line.substring(37, 39));
                        Timestamp rewardTime = new Timestamp(h, m, s);
                        ncrTimes.add(rewardTime);
                    }
                    catch (NumberFormatException e) {}
                }
            }
            readSource.close();
        }
        
        chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a log file");
        chooser.setSelectedFile(new File(System.getProperty("user.dir"), patid + "_session" + session_number + "_" + session_type + "_TicTimer_log.txt"));
        while ( true ){
            try {
                a9 = chooser.showSaveDialog(main_frame);
            } catch (Exception e) {}
            if ( a9 == JFileChooser.APPROVE_OPTION ) {
                log = chooser.getSelectedFile();
                if(log.exists()){
                    String confMessage = "The chosen log file already exists. Do you want to append to it?";
                    confMessage += "\nThis could cause problems if this is a DRZ run to be used later for NCR!";
                    int conf = JOptionPane.showConfirmDialog(main_frame, confMessage, "Append to existing log file?",JOptionPane.OK_CANCEL_OPTION);
                    if ( conf == JOptionPane.OK_OPTION ) {
                        JOptionPane.showMessageDialog(main_frame,"OK, Appending to existing log file", "Appending", JOptionPane.WARNING_MESSAGE);
                        break;
                    }
                }
                else
                    break;
            }
            if ( a9 == JFileChooser.CANCEL_OPTION ) return false;
        }
        
        main_frame.toFront();
        return true;
    }
    
    public void run() {
        setup_main_window();
    }
    
    public static void main(String[] args){
        /* Basic idea from port_test
         * Establish links and ask about button vs COM
         */
        
        ArrayList<CommPortIdentifier> cpis = listPorts();
        ArrayList<CommPortIdentifier> serial_cpis = new ArrayList<CommPortIdentifier>();
        CommPortIdentifier targetPI = null;
        int num_serial_ports = 0;
        
        for(CommPortIdentifier cpi: cpis){
            if(cpi.getPortType() == 1){ //Serial Port
                num_serial_ports++;
                serial_cpis.add(cpi);
                targetPI = cpi;
            }
        }
        if(num_serial_ports == 0){
            System.out.println("USB serial adapter not detected");
            if(boolPrompt("Are you using the button instead?"))
                reward_mode = RM_BUTTON;
            else/* Quit.
                 * Actually, I should probably say "plug it in" and scan again
                 */
                return;
        }
        else if(num_serial_ports == 1){
            /* This should really happen 95% of the time.
             * The other 4.95%, they probably just forgot to plug it in.
             */
            System.out.println("USB serial adapter detected at "+targetPI.getName());
            if(!boolPrompt("Is this the right port?")){
                if(boolPrompt("Would you like to quit?"))
                    //Again, this could be a "plug it in" prompt
                    return;
                System.out.println("Switching to button mode");
                reward_mode = RM_BUTTON;
            }
            //If they typed "y", just continue in LINK mode
        }
        else if(num_serial_ports > 1){
            System.out.println("Multiple serial devices detected:");
            for(int i=0; i<serial_cpis.size(); i++){
                System.out.println(i + ": " + serial_cpis.get(i).getName());
            }
            System.out.println("Which would you like to use? (type the number displayed before the correct port name)");
            String res = user_in.next();
            int resI = Integer.parseInt(res);
            //Not a valid index
            if(resI < 0 || resI > serial_cpis.size()){
                System.out.println("Switching to button mode");
                reward_mode = RM_BUTTON;
            }
            else{
                targetPI = serial_cpis.get(resI);
            }
        }
        //Continue and link
        if(reward_mode == RM_LINK){
            //Connect
            try{
                serialPort = (SerialPort) targetPI.open(appName, TIMEOUT);
                serialStream = serialPort.getOutputStream();
                System.out.println("Connection Established");
            }
            catch(Exception e){
                System.out.println("Exception: "+e.toString());
            }
        }
        
        //Start GUI
        tic_session = new TicTimer();
        tic_session.start();
    }
    
    public static ArrayList<CommPortIdentifier> listPorts(){
        ArrayList<CommPortIdentifier> cpis = new ArrayList<CommPortIdentifier>();
        CommPortIdentifier cpi = null;
        Enumeration ports = CommPortIdentifier.getPortIdentifiers();
        while(ports.hasMoreElements()){
            cpi = (CommPortIdentifier) ports.nextElement();
            cpis.add(cpi);
            System.out.println("Port Name: "+cpi.getName());
            String type = "";
            switch(cpi.getPortType()){
                //http://www.docjava.com/book/cgij/jdoc/constant-values.html#gnu.io.CommPortIdentifierInterface.PORT_PARALLEL
                case 1:
                    type = "Serial Port";
                    break;
                case 2:
                    type = "Parallel Port";
                    break;
                case 3:
                    type = "I2C Port";
                    break;
                case 4:
                    type = "RS385 Port";
                    break;
                case 5:
                    type = "Raw Port";
                    break;
            }
            System.out.println("\tPort Type: "+ type);
        }
        return cpis;
    }
    
    public static void endSession(){
        //Pause
        session_time_panel.pauseWatch();
        session_time_panel.reset();
        tic_time_panel.pauseWatch();
        tic_time_panel.reset();
        session_running = false;
        //Display and Logs
        progress_area.append("Session over\n");
        progressscroll.getVerticalScrollBar().setValue(TicTimer.progressscroll.getVerticalScrollBar().getMaximum());
        log_stream.println("Session " + TicTimer.session_number + " ended at " + TicTimer.clock_panel.getTimeAsString() + "\n");
        log_stream.close();
        //Buttons and Notification
        session_status_panel.setBackground(Color.RED);
        session_status_label.setText("Session status: ended");
        end_button.setEnabled(false);
        session_button.setEnabled(true); //- false. Or make it append.
        setup_button.setEnabled(true);
        tic_button.setEnabled(false);
        try{
            java.awt.Toolkit.getDefaultToolkit().beep();
            Thread.sleep(250);
            java.awt.Toolkit.getDefaultToolkit().beep();
            Thread.sleep(250);
            java.awt.Toolkit.getDefaultToolkit().beep();
            Thread.sleep(250);
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
        catch(Exception e){
            System.out.println("Exception: "+e.toString());
        }
    }
    
    public static void tic_detected(){
        if(session_running){
            tic_time_panel.reset();
            progress_area.append("Tic detected at " + session_time_panel.getTimeAsString() + "\n");
            progressscroll.getVerticalScrollBar().setValue(progressscroll.getVerticalScrollBar().getMaximum());
            log_stream.println("Tic detected at " + session_time_panel.getTimeAsString() + "\n");
        }
    }
    
    public static void tenIEnd(){
        // JOptionPane.showMessageDialog(main_frame,"REWARD!!!","Reward",JOptionPane.WARNING_MESSAGE);
        try {
            Thread.sleep(100);
        } catch (Exception e) {}
        
        progress_area.append("No tics for 10 seconds at " + session_time_panel.getTimeAsString() + "\n");
        log_stream.println("10s tic free interval ended at " + session_time_panel.getTimeAsString() + "\n");
        
        if(session_type.equals("DRZ")){
            send_reward();
        }
            
        //Scroll down to most current log entry
        progressscroll.getVerticalScrollBar().setValue(progressscroll.getVerticalScrollBar().getMaximum());
    }
    
    /**
     * Send a reward
     */
    public static void send_reward() {
        if(try_reward()){
            progress_area.append("Reward dispensed at " + session_time_panel.getTimeAsString() + "\n");
            log_stream.println("Reward dispensed at " + session_time_panel.getTimeAsString() + "\n");
        }
        else {
            progress_area.append("Reward failed to send at " + session_time_panel.getTimeAsString() + "\n");
            log_stream.println("Reward failed to send at " + session_time_panel.getTimeAsString() + "\n");
        }
    }
    /**
     * Send a reward. Return true if successful
     */
    public static boolean try_reward(){
        //Tell the user to press the dispense button
        //JOptionPane.showMessageDialog(main_frame,"REWARD!!!","Reward",JOptionPane.WARNING_MESSAGE);
        Thread beep = new Thread(){
            public void run(){
                try{
                    if(reward_mode == RM_BUTTON)
                        java.awt.Toolkit.getDefaultToolkit().beep();
                    else if(reward_mode == RM_LINK){
                        /* Send a pulse to the serial port (8 bytes long)
                         * The thing is, it's really probably time-based, 
                         * so this may need to increase a bit
                         */
                        for(int i = 0; i < 8; i++)
                            serialStream.write(0);
                    }
                    reward_notification_label.setBackground(Color.RED);
                    reward_notification_label.setText("SEND REWARD");
                    //Stay red for 0.5s
                    Thread.sleep(500);
                    reward_notification_label.setBackground(Color.WHITE);
                    reward_notification_label.setText("");
                }
                catch(Exception e){
                    System.out.println("Exception: "+e.toString());
                }
            }
        };
        beep.start();
        /*
         * If we ever get feedback from the machine, replace "return true"
         * with whatever code returns a success or failure boolean
         * from the machine's sensor
         */
        return true;
    }
    
    public void keyTyped(KeyEvent e) {
        char c = e.getKeyChar();
        //If the user typed space or t, take it as a detected tic
        if ( c == 't' || c == ' '){
            tic_detected();
        }
    }
    public void keyPressed(KeyEvent e) {
        // does nothing
    }
    public void keyReleased(KeyEvent e) {
        // does nothing
    }
    
    private static boolean boolPrompt(String prompt){
        String res = "";
        while(true){
            System.out.println(prompt + " (y/n)");
            res = user_in.next().toLowerCase(); //Non-case-sensitive
            if(res.equals("y"))
                return true;
            if(res.equals("n"))
                return false;
            System.out.println("Invalid option. Please type 'y' or 'n'.");
        }
        //If they typed "y" (or anything alse technically), just continue in LINK mode
    }
}