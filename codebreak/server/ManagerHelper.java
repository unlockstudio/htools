/*
    Code Break Manager Helper
    Copyright (C) 2008 Chris Eagle <cseagle at gmail d0t com>
    Copyright (C) 2008 Tim Vidas <tvidas at gmail d0t com>
    Copyright (C) 2010 XVilka <xvilka at gmail d0t com>

    This program is free software; you can redistribute it and/or modify it
    under the terms of the GNU General Public License as published by the Free
    Software Foundation; either version 2 of the License, or (at your option)
    any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT
    ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
    FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
    more details.

    You should have received a copy of the GNU General Public License along with
    this program; if not, write to the Free Software Foundation, Inc., 59 Temple
    Place, Suite 330, Boston, MA 02111-1307 USA
*/

package codebreak.server;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Properties;

/**
 * ManagerHelper
 * This class is intented to facilitate getting current status information to
 * the CodeBreakAdmin class.
 */

public class ManagerHelper extends Thread implements CodeBreakConstants {

    private static final String DEFAULT_PORT = "5043";
    private static final String DEFAULT_LOCAL = "1";

    private DataOutputStream dos;
    private ServerSocket ss;
    private Properties props = new Properties();
    private final ConnectionManagerBase cm;
    private int pidForUpdates;


    /**
     * very similary to the other constructor, execpt config paramters are attempted
     * to be read from a properties object p
     *
     * @param connm the connectionManager associated with this ManagerHelper
     * @param p     a propertied object (config file)
     * @throws Exception
     */
    public ManagerHelper(ConnectionManagerBase connm, Properties p) throws Exception {
        cm = connm;
        pidForUpdates = 0;
        props = p;
        initCommon();
    }

    /**
     * instantiates a new ManagerHelper with default parameters, the ManagerHelper
     * facilitates getting server state information to the CodeBreakAdmin
     *
     * @param connm the connectionManager associated with this ManagerHelper
     * @throws Exception
     */
    public ManagerHelper(ConnectionManagerBase connm) throws Exception {
        cm = connm;
        pidForUpdates = 0;
        initCommon();
    }

    private void initCommon() {
        try {
            int port = Integer.parseInt(props.getProperty("MANAGE_PORT", DEFAULT_PORT));
            int localonly = Integer.parseInt(props.getProperty("MANAGE_LOCAL", DEFAULT_LOCAL));
            if (localonly == 0) {
                ss = new ServerSocket(port);  //bind any
            } else {
                ss = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"));
            }
        } catch (Exception e) {
            logln("Could not setup ManagerHelper socket");
        }
        boolean dbMode = props.getProperty("SERVER_MODE", "database").equals("database");
    }

    /**
     * send_data constructs the packet and sends it to the CodeBreakAdmin
     *
     * @param command the server command to send
     * @param data    the data relevant to be sent with command
     */
    protected void send_data(int command, byte[] data) {
        try {
            if (command >= MNG_CONTROL_FIRST) {
                dos.writeInt(8 + data.length);
                dos.writeInt(command);
                dos.write(data);
                dos.flush();
                logln("send_data- cmd: " + command + " datasize: " + data.length, LDEBUG);
            } else {
                logln("post should be used for command " + command + ", not send_data.  Data not sent.", LERROR);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * run kicks off a thread that perpetually waits for a single connection, if the connection is dropped
     * it waits again, once connected, the ManagerHelper processes commands similar to the server.
     */
    public void run() {
        try {
            logln("ManagerHelper running...", LINFO);
            //just accept a single connection, loop back if the connection drops
            while (true) {
                try {
                    Socket s = ss.accept();
                    DataInputStream dis = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                    dos = new DataOutputStream(s.getOutputStream());
                    logln("New Management connection: " + s.getInetAddress().getHostAddress() + ":" + s.getPort(), LINFO);
                    while (true) {
                        CodeBreakOutputStream os = new CodeBreakOutputStream();
                        int len = dis.readInt();
                        int cmd = dis.readInt();

                        switch (cmd) {
                            case MNG_GET_CONNECTIONS: {
                                logln("sending connections", LINFO3);
                                String c = cm.listConnections();
                                os.writeUTF(c);
                                send_data(MNG_CONNECTIONS, os.toByteArray());
                                break;
                            }
                            case MNG_GET_STATS: {
                                logln("sending stats", LINFO3);
                                String c = cm.dumpStats();
                                os.writeUTF(c);
                                send_data(MNG_STATS, os.toByteArray());
                                break;
                            }
                            case MNG_SHUTDOWN: {
                                logln("client requested server shutdown", LINFO);
                                cm.Shutdown();
                                break;
                            }
                            case MNG_PROJECT_MIGRATE: {
                                logln("client requested a project migrate", LINFO);
                                //Client c = new Client(cm,new Socket());
                                byte[] md5_bytes = new byte[MD5_SIZE];
                                byte[] gpid_bytes = new byte[GPID_SIZE];
                                int status = MNG_MIGRATE_REPLY_FAIL;
                                try {
                                    int uid = dis.readInt();
                                    dis.readFully(gpid_bytes);
                                    String gpid = Utils.toHexString(gpid_bytes);
                                    dis.readFully(md5_bytes);
                                    String hash = Utils.toHexString(md5_bytes);
                                    String desc = dis.readUTF();
                                    long pub = dis.readLong() & 0x7FFFFFFF;
                                    long sub = dis.readLong() & 0x7FFFFFFF;

                                    int newpid = cm.migrateProject(uid, gpid, hash, desc, pub, sub);
                                    if (newpid > 0) {
                                        logln("Added new project " + newpid + " via project migration from another server");
                                        status = MNG_MIGRATE_REPLY_SUCCESS;
                                        pidForUpdates = newpid;  //store globally for any updates that may come in
                                    } else {
                                        logln("migrate project failed for gpid " + gpid + " hash " + hash);
                                        status = MNG_MIGRATE_REPLY_FAIL;
                                    }
                                    os.writeInt(status);
                                    send_data(MNG_PROJECT_MIGRATE_REPLY, os.toByteArray());
                                } catch (Exception ex) {
                                    logln("Malformed PROJECT MIGRATE", LERROR);
                                    break;
                                }
                                break;
                            }
                            case MNG_MIGRATE_UPDATE: {
                                logln("in MNG_MIGRATE_UPDATE", LERROR);
                                int uid = dis.readInt();
                                logln("... got uid" + uid, LERROR);
                                int pid = dis.readInt();
                                logln("... got pid" + pid, LERROR);
                                int ucmd = dis.readInt();
                                logln("... got cmd" + ucmd, LERROR);
                                int datalen = dis.readInt();
                                logln("... got datalen" + datalen, LERROR);
                                byte[] data = new byte[datalen];
                                dis.readFully(data);
                                logln("... got data", LERROR);
                                cm.migrateUpdate(uid, pidForUpdates, ucmd, data);
                                break;
                            }
                            default: {
                                logln("unkown command", LERROR);
//The CodeBreakAdmin has no means of processing this message as it is very much
//a synchronous protocol: Send Command -> Process Reply.  If we don't recognize
//their command we can easily drop it, but they are not likely to be looking
//for our reply
//                        os.writeUTF("bad command received:" + cmd);
//                        send_data(MNG_CONNECTIONS, os.toByteArray());
                            }
                        }
                    }
                } catch (EOFException e) {
                    logln("Manager connection dropped ", LINFO);
                }
            }
        } catch (Exception ex) {
            logln("ManagerHelper terminating: " + ex.getMessage(), LINFO);
            ex.printStackTrace();
        }
    }

    /**
     * closes the socket
     */
    protected void terminate() {
        try {
            ss.close();
        } catch (Exception ignored) {
        }
    }

    /**
     * logs a message to the configured log file (in the ConnectionManager)
     *
     * @param msg the string to log
     */
    protected void log(String msg) {
        log(msg, 0);
    }

    /**
     * logs a message to the configured log file (in the ConnectionManager)
     *
     * @param msg the string to log
     * @param v   apply a verbosity level to the msg
     */
    protected void log(String msg, int v) {
        String clientIP = null;
        String user = "";
        cm.log("[MNG]" + msg, v);
    }

    /**
     * logs a message using log() (with newline)
     *
     * @param msg the string to log
     * @param v   apply a verbosity level to the msg
     */
    protected void logln(String msg, int v) {
        log(msg + "\n", v);
    }

    /**
     * logs a message using log() (with newline)
     *
     * @param msg the string to log
     */
    protected void logln(String msg) {
        logln(msg, 0);
    }

    /**
     * logs an exception to the configured log file (in the ConnectionManager)
     *
     * @param ex the execption to log
     * @param v  apply a verbosity level to the ex
     */
    protected void logex(Exception ex, int v) {
        logln("Exception, trace follows:", v);
        cm.logex(ex, v);
    }

    /**
     * logs an exception to the configured log file (in the ConnectionManager)
     *
     * @param ex the execption to log
     */
    protected void logex(Exception ex) {
        logex(ex, 0);
    }

}

