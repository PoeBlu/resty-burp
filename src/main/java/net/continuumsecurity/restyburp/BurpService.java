/*******************************************************************************
 * BDD-Security, application security testing framework
 * 
 * Copyright (C) `2012 Stephen de Vries`
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see `<http://www.gnu.org/licenses/>`.
 ******************************************************************************/
/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package net.continuumsecurity.restyburp;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.continuumsecurity.restyburp.model.HttpRequestResponseBean;
import net.continuumsecurity.restyburp.model.ScanIssueBean;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import burp.BurpExtender;
import burp.IHttpRequestResponse;

import com.sun.grizzly.http.SelectorThread;
import com.sun.jersey.api.container.grizzly.GrizzlyWebContainerFactory;

/**
 *
 * @author stephen
 */
public class BurpService implements IBurpService {
    static final String WS_URI = "http://localhost:8181/";

    static Logger log = Logger.getLogger(BurpExtender.class.toString());
    BurpExtender extender;
    //Map of scanIds to ScanQueueMaps
    Map<Integer, ScanQueueMap> scans = new HashMap<Integer, ScanQueueMap>();
    int currentScanId = 0;
    static boolean headless = false;
    static String configFile = null;
    private static BurpService instance;

    private BurpService() {
    	PropertyConfigurator.configure("log4j.properties");
        log.debug("Creating new burp service");
        System.setProperty("java.awt.headless", Boolean.toString(headless));
        burp.StartBurp.main(new String[0]);
        extender = BurpExtender.getInstance();
        Map<String,String> config = new HashMap<String,String>();
        config.put("proxy.interceptrequests","false");
        updateConfig(config);
        try {
            reset();
        } catch (Exception ex) {
            throw new RuntimeException("Could not reset with blank state file: blank.burp.state");
        }
        extender.getCallbacks().setProxyInterceptionEnabled(false);
        assert extender != null;
    }
    
    public static BurpService getInstance() {
        if (instance == null) {
            instance = new BurpService();
        }
        return instance;
    }
 
    public void stop() {
        scans = null;
        extender.getCallbacks().exitSuite(false);
    }
    
    @Override
    public Map<String, String> getConfig() {
        return extender.getCallbacks().saveConfig();
    }
    
    public BurpExtender getExtender() {
        return extender;
    }
    
    @Override
    public void setConfig(Map<String, String> newConfig) {
        extender.getCallbacks().loadConfig(newConfig);
        log.debug("New config set: "+mapToString(newConfig));
    }
    
    public String mapToString(Map<String,String> theMap) {
        StringBuilder sb = new StringBuilder();
        for (String key : theMap.keySet()) {
            sb.append("\n"+key+"="+theMap.get(key));
        }
        return sb.toString();
    }
    
    @Override
    public void updateConfig(Map<String,String> newConfig) {
        Map<String,String> existingConfig = extender.getCallbacks().saveConfig();
        existingConfig.putAll(newConfig);
        extender.getCallbacks().loadConfig(existingConfig);
        log.debug("Updated config: "+mapToString(newConfig));
    }

    @Override
    public void saveConfig(String filename) {
        FileOutputStream out = null;
        try {
            Map<String, String> config = extender.getCallbacks().saveConfig();
            out = new FileOutputStream(filename);
            Properties props = new Properties();
            props.putAll(config);
            props.store(out, "Saved: " + new Date().toString());
            out.close();
            log.debug("Config saved to: " + filename);
        } catch (Exception ex) {
            //TODO Fix this catchall block
            Logger.getLogger(BurpExtender.class.getName()).error(ex);
        } finally {
            try {
                out.close();
            } catch (IOException ex) {
                Logger.getLogger(BurpExtender.class.getName()).error(ex);
            }
        }
    }

    
    @Override
    public void loadConfig(String filename) {
        FileInputStream in = null;
        try {
            extender.setConfigFile(filename);
            in = new FileInputStream(filename);
            Properties props = new Properties();
            props.load(in);
            extender.getCallbacks().loadConfig(props);
            in.close();
            log.debug("Config loaded from: " + filename);
        } catch (Exception ex) {
            //TODO Fix this catchall block
            Logger.getLogger(BurpExtender.class.getName()).error(ex);
        } finally {
            try {
                in.close();
            } catch (IOException ex) {
                Logger.getLogger(BurpExtender.class.getName()).error(ex);
            }
        }
    }

    @Override
    public synchronized int scan(String target) throws MalformedURLException {
        URL targetUrl = new URL(target);
        currentScanId++;
        scans.put(currentScanId, extender.scan(targetUrl.toExternalForm()));
        return currentScanId;
    }
    
    /*
     * Returns true if the url is in the queue or has already been scanned during this session
     */
    public boolean alreadyScanned(String target) {
        log.debug(" Checking if already scanned: "+target);
        for (ScanQueueMap sqm : scans.values()) {
            for (String url : sqm.getUrls()) {
                log.debug("  already scanned: "+url);
                if (url.equalsIgnoreCase(target)) {
                    log.debug(" Already scanned.");
                    return true;
                }
            }
            
        }
        log.debug(" Not already scanned");
        return false;
    }

    @Override
    public int getPercentageComplete(int scanId) {
        ScanQueueMap sqm = scans.get(scanId);
        if (sqm == null) {
            throw new ScanNotFoundException("Scan ID not found: " + scanId);
        }
        return sqm.getPercentageComplete();
    }

    @Override
    public List<ScanIssueBean> getIssues(int scanId) {
        ScanQueueMap sqm = scans.get(scanId);
        if (sqm == null) {
            throw new ScanNotFoundException("Scan ID not found: " + scanId);
        }
        return sqm.getIssues();
    }
    
    /*
     * Matches using the following regex options:
     * Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE)
     */
    @Override
    public HttpRequestResponseBean findInRequestHistory(String regex) {
    	Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE);
        for (HttpRequestResponseBean bean : getProxyHistory()) {
        	String stringBean;
			try {
				//TODO read encoding from headers
				//TODO Bug here, for some long pages (>40 000 BYTES) the matcher hangs.
				stringBean = new String(bean.getRequest(),"UTF8");
				log.debug("Searching in request: "+bean.getUrl());
	            if (p.matcher(stringBean).matches()) {
	            	log.debug("Found regex: "+regex+" in request: "+bean.getUrl());
	            	return bean;
	            }
			} catch (UnsupportedEncodingException e) {
				log.error(e.getMessage());
				e.printStackTrace();
			}
        	
        }
        log.debug("Did not find regex: "+regex+" in response history.");
        return null;
    }
    
    @Override
    public HttpRequestResponseBean findInResponseHistory(String regex) {
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.MULTILINE);
        for (HttpRequestResponseBean bean : getProxyHistory()) {
        	String stringBean;
			try {
				//TODO read encoding from headers
				//TODO Bug here, for some long pages (>40 000 BYTES) the matcher hangs.
				stringBean = new String(bean.getResponse(),"UTF8");
				log.debug("Searching in response to: "+bean.getUrl());
	            if (p.matcher(stringBean).matches()) {
	            	log.debug("Found regex: "+regex+" in response: "+bean.getUrl());
	            	return bean;
	            }
			} catch (UnsupportedEncodingException e) {
				log.error(e.getMessage());
				e.printStackTrace();
			}
        	
        }
        log.debug("Did not find regex: "+regex+" in response history.");
        return null;
    }

    @Override
    public List<HttpRequestResponseBean> getProxyHistory() {
        List<HttpRequestResponseBean> result = new ArrayList<HttpRequestResponseBean>();
        for (IHttpRequestResponse ihrr : extender.getProxyHistory()) {
            result.add(new HttpRequestResponseBean(ihrr));
        }
        log.debug("Returning "+result.size()+" request/responses from the proxy history.");
        return result;
    }

    @Override
    public List<HttpRequestResponseBean> getProxyHistory(String url) throws Exception {
        List<HttpRequestResponseBean> result = new ArrayList<HttpRequestResponseBean>();
        for (IHttpRequestResponse ihrr : extender.getProxyHistory()) {
            if (ihrr.getUrl().sameFile(new URL(url))) {
                result.add(new HttpRequestResponseBean(ihrr));
            }
        }
        return result;
    }

    @Override
    public void reset() throws Exception {
        scans.clear();
        extender.reset();
        log.debug("Burp state reset");
    }

    public void startRESTServer() {
        final Map<String, String> initParams = new HashMap<String, String>();
        initParams.put("com.sun.jersey.config.property.packages", "net.continuumsecurity.restyburp.server");
        // Using JAXB mapping method
        //initParams.put("com.sun.jersey.api.json.POJOMappingFeature", "true");
        log.debug("Starting REST server");
        new Thread() {

            public void run() {
                try {
                    SelectorThread threadSelector = GrizzlyWebContainerFactory.create(WS_URI, initParams);
                    log.debug(String.format("Jersey app started with WADL available at %sapplication.wadl\n", WS_URI, WS_URI));
                } catch (IOException ex) {
                    ex.printStackTrace();
                } catch (IllegalArgumentException ex) {
                    ex.printStackTrace();
                }
            }
        }.start();
    }

    public static void main(String... args) {
        
        OptionParser parser = new OptionParser();
        parser.accepts("f").withOptionalArg().ofType(String.class);
        parser.accepts("g");
        OptionSet options = parser.parse(args);

        if (options.has("g")) {
            headless = false;
        }
        
        BurpService bs = BurpService.getInstance();
        
        if (options.has("f")) {
            if (options.hasArgument("f")) {
                bs.loadConfig((String) options.valueOf("f"));
            } else {
                bs.loadConfig("burp.config");
            }
        }
        
        bs.startRESTServer();
    }
}
