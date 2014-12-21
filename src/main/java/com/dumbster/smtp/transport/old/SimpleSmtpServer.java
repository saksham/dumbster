/*
 * Dumbster - a dummy SMTP server
 * Copyright 2004 Jason Paul Kitchen
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dumbster.smtp.transport.old;

import com.dumbster.smtp.transport.Observable;
import com.dumbster.smtp.transport.Observer;
import org.apache.log4j.Logger;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.IOException;

/**
 * SMTP server
 */
public class SimpleSmtpServer implements Observable<SmtpMessage>, Runnable {

  /**
   * General Logger for this Class.
   */
  private static final Logger LOG = Logger.getLogger(SimpleSmtpServer.class);

  private List<Observer<SmtpMessage>> observers = new ArrayList<>();

  /**
   * Default SMTP port is 25.
   */
  public static final int DEFAULT_SMTP_PORT = 25;

  /**
   * Indicates whether this server is stopped or not.
   */
  private volatile boolean stopped = true;

  /**
   * Handle to the server socket this server listens to.
   */
  private ServerSocket serverSocket;

  /**
   * Port the server listens on - set to the default SMTP port initially.
   */
  private int port = DEFAULT_SMTP_PORT;

  /**
   * Timeout listening on server socket.
   */
  private static final int TIMEOUT = 500;

  /**
   * Constructor.
   *
   * @param port port number
   */
  public SimpleSmtpServer(int port) {
    this.port = port;
  }

  /**
   * Main loop of the SMTP server.
   */
  public void run() {
    try {
      try {
        serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(TIMEOUT); // Block for maximum of 1.5 seconds
        stopped = false;
      } finally {
        synchronized (this) {
          // Notify when server socket has been created
          notifyAll();
        }
      }

      LOG.info("Started SMTP server on port: " + port);
      // Server: loop until stopped
      while (!isStopped()) {
        // Start server socket and listen for client connections
        Socket socket = null;
        try {
          socket = serverSocket.accept();
        } catch (Exception e) {
          if (socket != null) {
            socket.close();
          }
          continue; // Non-blocking socket timeout occurred: try accept() again
        }

        // Get the input and output streams
        BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), "US-ASCII"));
        PrintWriter out = new PrintWriter(socket.getOutputStream());

        handleTransaction(out, input);

        socket.close();
      }
    } catch (Exception e) {
      /** TODO Should throw an appropriate exception here. */
      LOG.error(e.getMessage(), e);
    } finally {
      if (serverSocket != null) {
        try {
          serverSocket.close();
        } catch (IOException e) {
          LOG.error(e.getMessage(), e);
        }
      }
      LOG.info("Stopped SMTP server");
    }
  }

  /**
   * Check if the server has been placed in a stopped state. Allows another thread to
   * stop the server safely.
   *
   * @return true if the server has been sent a stop signal, false otherwise
   */
  public synchronized boolean isStopped() {
    return stopped;
  }

  /**
   * Stops the server. Server is shutdown after processing of the current request is complete.
   */
  public synchronized void stop() {
    // Mark as closed
    stopped = true;
    try {
      // Kick the server accept loop
      serverSocket.close();
    } catch (IOException e) {
      // Ignore
    }
  }

  /**
   * Handle an SMTP transaction, i.e. all activity between initial connect and QUIT command.
   *
   * @param out   output stream
   * @param input input stream
   * @throws IOException
   */
  private void handleTransaction(PrintWriter out, BufferedReader input) throws IOException {
    // Initialize the state machine
    SmtpState smtpState = SmtpState.CONNECT;
    SmtpRequest smtpRequest = new SmtpRequest(SmtpActionType.CONNECT, "", smtpState);

    // Execute the connection request
    SmtpResponse smtpResponse = smtpRequest.execute();

    // Send initial response
    sendResponse(out, smtpResponse);
    smtpState = smtpResponse.getNextState();

    SmtpMessage msg = new SmtpMessage();

    while (smtpState != SmtpState.CONNECT) {
      String line = input.readLine();

      if (line == null) {
        break;
      }
      System.out.println("LINE: " + line);

      // Create request from client input and current state
      SmtpRequest request = SmtpRequest.createRequest(line, smtpState);
      // Execute request and create response object
      SmtpResponse response = request.execute();
      // Move to next internal state
      smtpState = response.getNextState();
      // Send reponse to client
      sendResponse(out, response);

      // Store input in message
      String params = request.getParams();
      msg.store(response, params);

      // If message reception is complete save it
      if (smtpState == SmtpState.QUIT) {
        msg.close();
        notifyAllObservers(msg);
        msg = new SmtpMessage();
      }
    }
  }


  private void notifyAllObservers(SmtpMessage smtpMessage) {
    for(Observer<SmtpMessage> observer : observers) {
      observer.added(smtpMessage);
    }
  }

  /**
   * Send response to client.
   * @param out socket output stream
   * @param smtpResponse response object
   */
  private static void sendResponse(PrintWriter out, SmtpResponse smtpResponse) {
    if (smtpResponse.getCode() > 0) {
      int code = smtpResponse.getCode();
      String message = smtpResponse.getMessage();
      out.print(code + " " + message + "\r\n");
      out.flush();
    }
  }

  
  /**
   * Creates an instance of SimpleSmtpServer and starts it. Will listen on the default port.
   * @return a reference to the SMTP server
   */
  public static SimpleSmtpServer start() {
    return start(DEFAULT_SMTP_PORT);
  }

  /**
   * Creates an instance of SimpleSmtpServer and starts it.
   * @param port port number the server should listen to
   * @return a reference to the SMTP server
   */
  public static SimpleSmtpServer start(int port) {
    SimpleSmtpServer server = new SimpleSmtpServer(port);
    Thread t = new Thread(server);
    
    synchronized (server) {
      t.start();

      // Block until the server socket is created
      try {
        server.wait();
      } catch (InterruptedException e) {
        // Ignore don't care.
      }
    }
    return server.isStopped() ? null : server;
  }

  @Override
  public void addObserver(Observer<SmtpMessage> observer) {
    this.observers.add(observer);
  }

  @Override
  public void removeObserver(Observer<SmtpMessage> observer) {
    this.observers.remove(observer);
  }


  public static void main(String[] args) throws Exception {
    SimpleSmtpServer server = SimpleSmtpServer.start(2560);
    System.in.read();
  }
}
