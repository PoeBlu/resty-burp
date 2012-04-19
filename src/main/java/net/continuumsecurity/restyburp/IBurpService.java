
package net.continuumsecurity.restyburp;

import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;
import net.continuumsecurity.restyburp.model.HttpRequestResponseBean;
import net.continuumsecurity.restyburp.model.ScanIssueBean;

 
 

public interface IBurpService {
	 
	//  Scan a given URL.  Will scan based on the traffic already captured by burp.
    int scan(String target) throws MalformedURLException;   
     
    //  Returns 0-100 depending on the completion of a scan
    int getPercentageComplete(int scanId);   
     
    // Returns a list of ScanIssueBean's that represent the issues found so far for a given scan ID. 
    List<ScanIssueBean> getIssues(int scanId); 
     
    // Return the whole proxy history.
    List<HttpRequestResponseBean> getProxyHistory();
     
    // Return only the proxy history that matches the given URL. 
    List<HttpRequestResponseBean> getProxyHistory(String url) throws Exception;
     
    // Restore Burp's state with that specified in the blank.burp.state file.
    void reset() throws Exception;
     
    // Return the current configuration.
    Map<String,String> getConfig();
     
    // Replace the current configuration with the specified one.
    void setConfig(Map<String,String> newConfig);
     
    // Keep the current configuration and only update those items provided.
    void updateConfig(Map<String,String> newConfig);
      
    // Save the current config to a file
    void saveConfig(String filename);
     
    // Load the configuration from a file.
    void loadConfig(String filename);
    
    HttpRequestResponseBean findInResponseHistory(String regex);
    HttpRequestResponseBean findInRequestHistory(String regex);
}
