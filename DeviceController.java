package com.example.PeripheralService.Controllers;

import com.example.PeripheralService.Messages;
import com.example.PeripheralService.Models.JSONHandler;
import com.example.PeripheralService.Models.LocationInfo;
import com.example.PeripheralService.Models.Logs;
import com.example.PeripheralService.Models.Tablet;
import com.example.PeripheralService.Models.response.ErrorResponse;
import com.example.PeripheralService.Models.response.Response;
import com.example.PeripheralService.Models.response.SuccessResponse;
import com.example.PeripheralService.Services.*;
import com.example.PeripheralService.exception.PeripheralServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.JSONPObject;
//import com.hexwave.fluentd.logs.FluentLogFormat;
import com.yoctopuce.YoctoAPI.YAPI_Exception;
import java.io.*;
import java.lang.reflect.Method;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import com.example.PeripheralService.Services.ReadHexwaveConfigService;
import com.example.PeripheralService.utils.LightColor;
import com.example.PeripheralService.utils.LightLocation;
import com.example.PeripheralService.Models.Device;
import org.json.JSONException;
import org.json.JSONObject;
import com.example.PeripheralService.utils.UIState;

import java.util.concurrent.Future;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/device")
@Slf4j
@EnableScheduling
public class DeviceController {

	@Autowired
	private DeviceService deviceService;

	@Autowired
	private LightService lightService;

	@Autowired
	private ConfigService config;

	@Autowired
	private ReadHexwaveConfigService hexwaveConfig;

	@Autowired
	private HttpServletRequest request;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Value("${my.frameNumber}")
	private String frameNumber;

	@Value("${my.distance}")
	private String distance;
	
    @Value("${callibration.cron.enabled}")
    private boolean cronEnabled;


	
	// private FluentLogFormat fLog = new FluentLogFormat();
	// Marker confidentialMarker = MarkerFactory.getMarker("PeripheralService");

	public enum deviceType {
		light, sound
	}

	int id = 1; // GO Not sure why this is needed

	// for now when this API is called we just show the left light
	// The UI will call this API so that can identify th devices
	// In order to avoid the overhead of using a DB and matching configurations
	// we just use left lights.

	@GetMapping("/detect/{macId}")
	public Response validateMacAddrAndConnectedUSBDevices(@PathVariable String macId)
			throws IOException, YAPI_Exception, InterruptedException {

		log.info("# Detect a device STATUS mac address: {}, Request from {}", macId,
				request.getRemoteHost());
		Map<String, Object> data = config.readYAMLConfigFile();
		Map<String, String> hexConfig = hexwaveConfig.readHexwaveConfigFile();
		Response response = null;

		try {
			log.info("# CALLING DB TO GET GET DEVICE INFO");

			Device device = deviceService.getIPAddrFromMac(macId);

			log.info("# DONE CALLING DB TO GET GET DEVICE INFO");

			// Now that we have the device information, find out if this our device
			// HEXWAVE_HOST_ID=ai-pc
			// HEXWAVE_EXTERNAL_IP=172.16.2.129
			String mapKey = "HEXWAVE_EXTERNAL_IP";
			// if(hexConfig.containsKey(mapKey))
			LightColor color = LightColor.valueOf("TEST");

			String localIPAddress = hexConfig.getOrDefault(mapKey, "");
			log.info("# local IP address: " + localIPAddress);
			log.info("# Device IP address: " + device.getIp_address());

			if (localIPAddress.trim().equalsIgnoreCase(device.getIp_address().trim())) {

				log.info("# Detect a device on this machine with ### " + macId);

				lightService.blinkLight(LightLocation.FRONT, color);
				lightService.blinkLight(LightLocation.BACK, color);
				lightService.blinkLight(LightLocation.LEFT, color);
				lightService.blinkLight(LightLocation.RIGHT, color);

				response = new SuccessResponse(Messages.light_blink_ok + color,
						HttpStatus.OK.value());
			} else {// send to paired device if this mac address does not match local is

				// String peerIPAddress = data.get("peer").toString();
				String peerIPAddress = data.getOrDefault("peer", "").toString();

				log.info("# Mac address not found on this machine:  " + macId);
				log.info("# Mac address not found on this machine. Sending to paired IP: " +
						peerIPAddress);

				if (!StringUtils.isEmpty(peerIPAddress)) {
					response = makeGetApiCallDetectDevice(peerIPAddress, macId);
				} else {
					log.info("# No peered devices");
				}

			}
		} catch (Exception e) {
			response = new ErrorResponse(e.getMessage(),
					HttpStatus.INTERNAL_SERVER_ERROR.value());
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					"PeripheralServiceException", "SOME IP Not needed",
					e.getMessage(), "")));
		}
		return response;
	}

	// mimic a ping to display lights on the device. This will also be used to
	// detect the devices
	@GetMapping("/ping")
	public Response pingLightsOnDevice()
			throws IOException, YAPI_Exception, InterruptedException {

		log.info("# Ping device and display all lights. Request from {}", request.getRemoteHost());

		Response response = null;

		try {
			LightColor color = LightColor.valueOf("TEST");
			log.info("# Ping device and display all lights ### ");

			lightService.blinkLight(LightLocation.FRONT, color);
			lightService.blinkLight(LightLocation.BACK, color);
			lightService.blinkLight(LightLocation.LEFT, color);
			lightService.blinkLight(LightLocation.RIGHT, color);

			response = new SuccessResponse(Messages.light_blink_ok + color,
					HttpStatus.OK.value());

		} catch (Exception e) {
			response = new ErrorResponse(e.getMessage(),
					HttpStatus.INTERNAL_SERVER_ERROR.value());
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					"PeripheralServiceException", "SOME IP Not needed",
					e.getMessage(), "")));
		}
		return response;
	}

	/*
	 * @GetMapping("/detect/{macId}")
	 * public Response validateMacAddrAndConnectedUSBDevices(@PathVariable String
	 * macId)
	 * throws IOException, YAPI_Exception, InterruptedException {
	 * 
	 * log.info("# Detect a device STATUS mac address: {}, Request from {}", macId,
	 * request.getRemoteHost());
	 * Map<String, Object> data = config.readYAMLConfigFile();
	 * Map<String, String> hexConfig = hexwaveConfig.readHexwaveConfigFile();
	 * Response response = null;
	 * 
	 * try {
	 * 
	 * // hack for now until we sync the DB
	 * 
	 * /////
	 * String peerIPAddress = data.get("peer").toString();
	 * if (!StringUtils.isEmpty(peerIPAddress)) {
	 * 
	 * ///////
	 * log.info("# CALLING DB TO GET GET DEVICE INFO");
	 * 
	 * Device device = deviceService.getIPAddrFromMac(macId);
	 * 
	 * log.info("# DONE CALLING DB TO GET GET DEVICE INFO");
	 * 
	 * // Now that we have the device information, find out if this our device
	 * // HEXWAVE_HOST_ID=ai-pc
	 * // HEXWAVE_EXTERNAL_IP=172.16.2.129
	 * String mapKey = "HEXWAVE_EXTERNAL_IP";
	 * // if(hexConfig.containsKey(mapKey))
	 * LightColor color = LightColor.valueOf("TEST");
	 * 
	 * String localIPAddress = hexConfig.getOrDefault(mapKey, "");
	 * log.info("# local IP address: " + localIPAddress);
	 * log.info("# Device IP address: " + device.getIp_address());
	 * 
	 * if (localIPAddress.trim().equalsIgnoreCase(device.getIp_address().trim())) {
	 * 
	 * log.info("# Detect a device on this machine with ### " + macId);
	 * 
	 * lightService.blinkLight(LightLocation.FRONT, color);
	 * lightService.blinkLight(LightLocation.BACK, color);
	 * lightService.blinkLight(LightLocation.LEFT, color);
	 * lightService.blinkLight(LightLocation.RIGHT, color);
	 * 
	 * response = new SuccessResponse(Messages.light_blink_ok + color,
	 * HttpStatus.OK.value());
	 * } else {// send to paired device if this mac address does not match local is
	 * the
	 * 
	 * //peerIPAddress = data.get("peer").toString();
	 * 
	 * log.info("# Mac address not found on this machine:  " + macId);
	 * log.info("# Mac address not found on this machine. Sending to paired IP: " +
	 * peerIPAddress);
	 * 
	 * if (!StringUtils.isEmpty(peerIPAddress)) {
	 * makeGetApiCallDetectDevice(peerIPAddress, macId);
	 * } else {
	 * log.info("# No peered devices");
	 * }
	 * 
	 * }
	 * 
	 * } else {
	 * log.info("# No peered devices, do local for now");
	 * LightColor color = LightColor.valueOf("TEST");
	 * lightService.blinkLight(LightLocation.FRONT, color);
	 * lightService.blinkLight(LightLocation.BACK, color);
	 * lightService.blinkLight(LightLocation.LEFT, color);
	 * lightService.blinkLight(LightLocation.RIGHT, color);
	 * 
	 * response = new SuccessResponse(Messages.light_blink_ok + color,
	 * HttpStatus.OK.value());
	 * }
	 * } catch (Exception e) {
	 * response = new ErrorResponse(e.getMessage(),
	 * HttpStatus.INTERNAL_SERVER_ERROR.value());
	 * log.error(String.valueOf(new Logs(id++, new
	 * Timestamp(System.currentTimeMillis()), "error",
	 * "PeripheralServiceException", "SOME IP Not needed",
	 * e.getMessage(), "")));
	 * }
	 * return response;
	 * }
	 */

	@GetMapping("/detectlight/off/{location}")
	public Response lightOffByLocation(@PathVariable LightLocation location)
			throws IOException, YAPI_Exception, InterruptedException {

		log.info("# START lightOffByLocation. Request from {}", request.getRemoteHost());

		Map<String, Object> data = config.readYAMLConfigFile();
		Response response = null;

		try {
			lightService.lightOff(location);
			response = new SuccessResponse(Messages.light_blink_ok + location, HttpStatus.OK.value());

		} catch (FileNotFoundException e) {
			response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					"PeripheralServiceException", "figure out Ipaddress later", e.getMessage(), "")));
		} catch (IOException e) {
			response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					"PeripheralServiceException", "figure out Ipaddress later", e.getMessage(), "")));
		} catch (Exception e) {
			response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					"PeripheralServiceException", "figure out Ipaddress later", e.getMessage(), "")));
		}

		// Since this might a paired device, send message to the paired device
		// String peerIPAddress = data.get("peer").toString();
		String peerIPAddress = data.getOrDefault("peer", "").toString();
		if (!StringUtils.isEmpty(peerIPAddress)) {
			makeGetApiCallDetectLight(peerIPAddress, location);
		} else {
			log.info("# No peered devices");
		}
		return response;
	}

	@GetMapping("/detectlight/{location}")
	public Response displayLightByLocation(@PathVariable LightLocation location)
			throws IOException, YAPI_Exception, InterruptedException {

		log.info("# START detectlight/location. Request from {}", request.getRemoteHost());

		Map<String, Object> data = config.readYAMLConfigFile();
		Response response = null;

		try {
			lightService.blinkLight(location, LightColor.TEST);
			response = new SuccessResponse(Messages.light_blink_ok + location, HttpStatus.OK.value());

		} catch (FileNotFoundException e) {
			response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					"PeripheralServiceException", "figure out Ipaddress later", e.getMessage(), "")));
		} catch (IOException e) {
			response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					"PeripheralServiceException", "figure out Ipaddress later", e.getMessage(), "")));
		} catch (Exception e) {
			response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					"PeripheralServiceException", "figure out Ipaddress later", e.getMessage(), "")));
		}

		// Since this might a paired device, send message to the paired device
		// String peerIPAddress = data.get("peer").toString();
		String peerIPAddress = data.getOrDefault("peer", "").toString();
		if (!StringUtils.isEmpty(peerIPAddress)) {
			makeGetApiCallDetectLight(peerIPAddress, location);
		} else {
			log.info("# No peered devices");
		}
		return response;
	}

	@GetMapping("/person-in-scene")
	public Response displayLightPersonInScene()
			throws IOException, YAPI_Exception, InterruptedException {
		log.info("# START CALLING person-in-scene. Request from {}", request.getRemoteHost());
		Response response = null;

		try {
			// Do you turn both on.. since we do non know the enntrance??????
			lightService.blinkLight(LightLocation.LEFT, LightColor.RED);// todo need to find out if this left or right
			lightService.blinkLight(LightLocation.RIGHT, LightColor.RED); // device

			if (LightService.uiState == UIState.CONTINUE_ON) { // we do not want to update the state if UI has the
				// continue button on... for the detection light
				log.info("# NOT doing anything with Middle lights UI STATUS state is {}", LightService.uiState.name());
			} else {
				log.info("# YES turning middle light off UI STATUS state is {}", LightService.uiState.name());
				log.info("###### TURN OFF MIDDLE LIGHTS");
				lightService.lightOff(LightLocation.FRONT);
				lightService.lightOff(LightLocation.BACK);

			}

			response = new SuccessResponse(Messages.light_blink_ok + LightLocation.LEFT.name(), HttpStatus.OK.value());
		} catch (FileNotFoundException e) {
			response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					"PeripheralServiceException", "figure out Ipaddress later", e.getMessage(), "")));
		} catch (IOException e) {
			response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					"PeripheralServiceException", "figure out Ipaddress later", e.getMessage(), "")));
		} catch (Exception e) {
			response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					"PeripheralServiceException", "figure out Ipaddress later", e.getMessage(), "")));
		}

		log.info("# STOP CALLING person-in-scene");
		return response;
	}

	@GetMapping("/no-person-in-scene")
	public Response displayLightNoPersonInScene()
			throws IOException, YAPI_Exception, InterruptedException {

		log.info("# START CALLING no-person-in-scene. Request from {}", request.getRemoteHost());
		Response response = null;

		try {
			// Do you turn both on.. since we do non know the enntrance??????

			if (LightService.uiState == UIState.CONTINUE_ON) { // we do not want to update the state if UI has the
				// continue button on...
				log.info("# NOT doing no-person-in-scene UI STATUS state is {}", LightService.uiState.name());
			} else {
				log.info("# YES doing no-person-in-scene UI STATUS state is {}", LightService.uiState.name());
				lightService.blinkLight(LightLocation.LEFT, LightColor.GREEN); // todo need to find out if this left or
				lightService.blinkLight(LightLocation.RIGHT, LightColor.GREEN); // right device
			}
			response = new SuccessResponse(Messages.light_blink_ok + LightLocation.LEFT, HttpStatus.OK.value());

		} catch (FileNotFoundException e) {
			response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					"PeripheralServiceException", "figure out Ipaddress later", e.getMessage(), "")));
		} catch (IOException e) {
			response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					"PeripheralServiceException", "figure out Ipaddress later", e.getMessage(), "")));
		} catch (Exception e) {
			response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					"PeripheralServiceException", "figure out Ipaddress later", e.getMessage(), "")));
		}
		log.info("# DONE CALLING no-person-in-scene");
		return response;
	}

	@PostMapping("/devicestatus")
	public Response displayDetectionResult(@RequestBody JSONHandler incoming)
			throws IOException, YAPI_Exception, InterruptedException {

		log.info("# Display devicestatus STATUS ### {} Request from {}", incoming.getStatus().name(),
				request.getRemoteHost());

		Map<String, Object> data = config.readYAMLConfigFile();

		log.info("## test 1");

		Response response;
		try {
			///// MUST CALLL CAllED IN PARALLEL
			lightService.blinkLight(LightLocation.FRONT, incoming.getStatus());
			lightService.blinkLight(LightLocation.BACK, incoming.getStatus());
			log.info("## test 2");
			response = new SuccessResponse(Messages.light_blink_ok + incoming.getStatus(), HttpStatus.OK.value());

		} catch (Exception e) {
			response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					Messages.light_error, "localhost", e.getMessage(), "")));
		}
		// Since this might a paired device, send message to the paired device
		log.info("## test 3");
		// String peerIPAddress = data.get("peer").toString();
		String peerIPAddress = data.getOrDefault("peer", "").toString();
		if (!StringUtils.isEmpty(peerIPAddress)) {
			log.info("# Sending DetectionResult STATUS to PEER at " + peerIPAddress);
			makeApiCallDetectionResult(peerIPAddress, incoming);
		} else {
			log.info("# No peered devices");
		}

		log.info("# Done Display DetectionResult STATUS ### " + incoming.getStatus().name());
		return response;
	}

	//////////////

	@GetMapping("/devices")
	public Response getListOfDevices()
			throws IOException, YAPI_Exception, InterruptedException {

		log.info("# START List of Devices {}", request.getRemoteHost());
		Response response = null;

		try {
			List<String> lights = null;
			log.info("1 To caller START List Getting Light Devices: ");

			Future<List<String>> future = lightService.getListOfSerialNumbers();

			while (true) {
				if (future.isDone()) {
					lights = future.get();
					break;
				}
				Thread.sleep(100);
			}

			for (String li : lights) {
				log.info("Back START List Getting Light Devices {}: ", li);
			}

			response = new SuccessResponse(lights, HttpStatus.OK.value());

		} catch (Exception e) {
			e.printStackTrace();
			response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					"PeripheralServiceException", "figure out Ipaddress later", e.getMessage(), e.getMessage())));
		}
		log.info("# DONE CALLING Get devices");
		return response;
	}

	@PutMapping("/assignSerialNumber")
	public Response assignSerialNumberForLocation(@RequestBody LocationInfo incoming)
			throws IOException, YAPI_Exception, InterruptedException {

		log.info("# Display assignSerialNumberForLocation ### {} Request from {}", incoming.toString(),
				request.getRemoteHost());
		Response response;
		try {
			Future<Boolean> future = lightService.assignDeviceToLocation(incoming);

			while (true) {
				if (future.isDone()) {
					boolean added = future.get();
					if (added) {
						response = new SuccessResponse(Messages.light_blink_ok + incoming.toString(),
								HttpStatus.OK.value());
					} else {
						response = new SuccessResponse(Messages.light_blink_ok + incoming.toString(),
								HttpStatus.BAD_REQUEST.value());
					}
					break;
				}
				Thread.sleep(100);
			}
		} catch (Exception e) {
			log.info("# Exception in assignSerialNumber");
			response = new ErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST.value());
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					Messages.light_error, "localhost", e.getMessage(), "")));
		}

		log.info("# Done with assignSerialNumberForLocation " + incoming.toString());
		return response;
	}

	////////////////

	@PostMapping("/detection-result")
	public Response displayAIDetectionResult(@RequestBody JSONHandler incoming)
			throws IOException, YAPI_Exception, InterruptedException {

		log.info("# Display DetectionResult STATUS ### {} Request from {}", incoming.getStatus().name(),
				request.getRemoteHost());

		Map<String, Object> data = config.readYAMLConfigFile();
		Response response;

		try {
			///// MUST BE CAllED IN PARALLEL
			switch (incoming.getStatus()) {
				case THREAT_DISPLAY_START:
					lightService.blinkLight(LightLocation.FRONT, incoming.getStatus());
					lightService.blinkLight(LightLocation.BACK, incoming.getStatus());
					lightService.blinkLight(LightLocation.RIGHT, incoming.getStatus());
					lightService.blinkLight(LightLocation.LEFT, incoming.getStatus());
					lightService.setState(UIState.CONTINUE_ON);
					break;
				case THREAT_DISPLAY_END:
					// lightService.blinkLight(LightLocation.FRONT, incoming.getStatus());
					// lightService.blinkLight(LightLocation.BACK, incoming.getStatus());
					log.info("###### TURN OFF MIDDLE LIGHTS");
					lightService.lightOff(LightLocation.FRONT);
					lightService.lightOff(LightLocation.BACK);
					lightService.blinkLight(LightLocation.RIGHT, incoming.getStatus());
					lightService.blinkLight(LightLocation.LEFT, incoming.getStatus());
					lightService.setState(UIState.CONTINUE_OFF);
					break;
				case THREAT:
					lightService.blinkLight(LightLocation.FRONT, incoming.getStatus());
					lightService.blinkLight(LightLocation.BACK, incoming.getStatus());
					if (deviceService.checkifPrimaryTabletIsConfigured() > 0) {
						lightService.blinkLight(LightLocation.RIGHT, incoming.getStatus());
						lightService.blinkLight(LightLocation.LEFT, incoming.getStatus());

						lightService.setState(UIState.CONTINUE_ON);
					}
					break;
				default:
					doOthers(incoming);
					break;
			}
			response = new SuccessResponse(Messages.light_blink_ok + incoming.getStatus(), HttpStatus.OK.value());

		} catch (Exception e) {
			log.info("# Testing EXECPTION");
			response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					Messages.light_error, "localhost", e.getMessage(), "")));
		}
		// Since this might a paired device, send message to the paired device
		// String peerIPAddress = data.get("peer").toString();

		String peerIPAddress = data.getOrDefault("peer", "").toString();

		if (!StringUtils.isEmpty(peerIPAddress)) {
			log.info("# Sending DetectionResult STATUS to PEER at " + peerIPAddress);
			if ((incoming.getStatus() == LightColor.THREAT) && (deviceService.checkifPrimaryTabletIsConfigured() > 0)) {
				log.info("# Changing state from {} to {} for Peer", incoming.getStatus(),
						LightColor.THREAT_DISPLAY_START.name());
				// NO CHANGE THE PAYLOAD FOR THE REMOTE TO SHOW THE CONTINUE BUTTON
				// CHANGE ME WHEN WE HAVE DB SYNCRHONIZATION
				incoming.setStatus(LightColor.THREAT_DISPLAY_START);

			}
			makeApiCallDetectionResultNew(peerIPAddress, incoming);
		} else {
			log.info("# No peered devices");
		}
		log.info("# Done with Display DetectionResult STATUS ### " + incoming.getStatus().name());
		return response;
	}

	private void doOthers(JSONHandler incoming) {
		try {
			log.info("# Current UI Status ### {}", LightService.uiState.name());
			if (LightService.uiState == UIState.CONTINUE_ON) { // we do not want to update the state if UI has the //
																// continue button on...
				log.info("# NOT Showing DetectionResult UI STATUS state is {}", LightService.uiState.name());
			} else {
				log.info("# YES Showing DetectionResult UI STATUS state is {}", LightService.uiState.name());
				lightService.blinkLight(LightLocation.FRONT, incoming.getStatus());
				lightService.blinkLight(LightLocation.BACK, incoming.getStatus());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Response makeGetApiCallDetectDevice(String ipAddress, String macId) throws SocketException {

		ResponseEntity<String> result = null;
		Response response = null;
		try {

			final String url = "http://" + ipAddress + ":9012/device/detect/" + macId;
			RestTemplate restTemplate = new RestTemplate();
			URI uri = new URI(url);
			result = restTemplate.getForEntity(uri, String.class);
			response = new SuccessResponse(result.getBody() + result.getStatusCode(), HttpStatus.OK.value());

		} catch (URISyntaxException e) {

			response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());

			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					Messages.light_error, "localhost", e.getMessage(), e.getReason())));
		} catch (Exception e) {
			response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					Messages.light_error, "localhost", e.getMessage(), "")));
		}

		return response;
	}

	public void makeGetApiCallDetectLight(String ipAddress, LightLocation location) throws SocketException {
		try {
			final String url = "http://" + ipAddress + ":9012/device/detectlight/" + location.name();
			RestTemplate restTemplate = new RestTemplate();
			URI uri = new URI(url);
			ResponseEntity<String> result = restTemplate.getForEntity(uri, String.class);
			log.error(result.getBody());

		} catch (URISyntaxException e) {
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					Messages.light_error, "localhost", e.getMessage(), e.getReason())));
		} catch (Exception e) {
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					Messages.light_error, "localhost", e.getMessage(), "")));
		}
	}

	public void makeApiCallDetectionResultNew(String ipAddress, JSONHandler incoming) {
		try {

			log.info("#### makeApiCallDetectionResultNew");
			final String uri = "http://" + ipAddress + ":9012/device/detection-result";

			RestTemplate restTemplate = new RestTemplate();
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			JSONObject alertObject = new JSONObject();
			alertObject.put("left_mac_address", incoming.getLeft_mac_address());
			alertObject.put("right_mac_address", incoming.getRight_mac_address());
			alertObject.put("status", incoming.getStatus().name());

			HttpEntity<String> request = new HttpEntity<String>(alertObject.toString(), headers);

			log.info("##Sending to peer at: " + uri);
			restTemplate.postForObject(uri, request, Object.class);
			log.info("##DONE endin to peer at: " + uri);
		} catch (Exception e) {
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					"Exception", "localhost", e.getMessage(), "")));
		}
	}

	public void makeApiCallDetectionResult(String ipAddress, JSONHandler incoming) {
		try {
			final String uri = "http://" + ipAddress + ":9012/device/";

			log.info("Sending to Peer: " + uri);

			MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
			headers.add("Content-Type", "application/json");

			RestTemplate restTemplate = new RestTemplate();
			restTemplate.getMessageConverters().add(new MappingJackson2HttpMessageConverter());

			ObjectMapper mapper = new ObjectMapper();

			ObjectNode reqBody = mapper.createObjectNode();
			reqBody.put("left_mac_address", incoming.getLeft_mac_address());
			reqBody.put("right_mac_address", incoming.getRight_mac_address());
			reqBody.put("status", incoming.getStatus().name());

			HttpEntity<String> request = new HttpEntity<>(reqBody.toString(), headers);
			restTemplate.postForObject(uri, request, Object.class);
		} catch (Exception e) {
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error", "Exception",
					"localhost", e.getMessage(), "")));
		}
	}


	@Scheduled(cron = "${interval-in-cron}") 
	public void scheduledMethodWrapper() throws IOException {
		if (cronEnabled) {
		String mapKey = "HEXWAVE_EXTERNAL_IP";
		Map<String, String> hexConfig = hexwaveConfig.readHexwaveConfigFile();
		String ipAddress = hexConfig.getOrDefault(mapKey, "");
		log.info("You IPADDRESS is " + ipAddress);
		
		if(ipAddress.isEmpty()){
			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
					"Exception", "localhost", "No IPaddress found from Hexwave.cfg", "")));
		}
		else{
			makeGetApiCallCallibration(ipAddress, frameNumber, distance);
		}
		}
//		makeGetApiCallCallibrationForOtherDevice(ipAddress, frameNumber, distance);

	}

	public Response makeGetApiCallCallibration(String ipAddress, String frame, String distance) throws SocketException {
		
		
		int retries = 2;
		int id = 1;
		log.info("Starting scheduler to callibrate first device");
		ResponseEntity<String> result = null;
		Response response = null;
		while (retries > 0) {
			try {
// First API call

				final String url = "http://" + ipAddress + ":8567/stop-fpga-interface";
				RestTemplate restTemplate = new RestTemplate();
				URI uri = new URI(url);
				result = restTemplate.getForEntity(uri, String.class);
				response = new SuccessResponse(result.getBody() + result.getStatusCode(), HttpStatus.OK.value());

// Second API call

				final String url2 = "http://" + ipAddress + ":8567/calibrate-air?num=" + frame;
				URI uri2 = new URI(url2);
				result = restTemplate.getForEntity(uri2, String.class);
				// Process the response of the second API call as needed
				response = new SuccessResponse(result.getBody() + result.getStatusCode(), HttpStatus.OK.value());
				log.info("Api Call for number of frame : " + response);
// Third API call

				final String url3 = "http://" + ipAddress + ":8567/process-image?zin=" + distance;
				URI uri3 = new URI(url3);
				result = restTemplate.getForEntity(uri3, String.class);
				response = new SuccessResponse(result.getStatusCode(), HttpStatus.OK.value());
				log.info("Api Call to process image : " + response);

// Fourth API call

				final String url4 = "http://" + ipAddress + ":8567/save-air";
				URI uri4 = new URI(url4);
				result = restTemplate.getForEntity(uri4, String.class);
				// Process the response of the second API call as needed
				response = new SuccessResponse(result.getBody() + result.getStatusCode(), HttpStatus.OK.value());
				log.info("Api Call to save air : " + response);
// Fifth API call

				final String url5 = "http://" + ipAddress + ":8567/start-fpga-interface";
				URI uri5 = new URI(url5);
				result = restTemplate.getForEntity(uri5, String.class);
				response = new SuccessResponse(result.getBody() + result.getStatusCode(), HttpStatus.OK.value());
				log.info("Api Call to start interface : " + response);

				return response;
			} catch (URISyntaxException e) {
				retries--;
				if (retries == 0) {
					response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());

					log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
							"Exception", "localhost", e.getMessage(), e.getReason())));
					break;
				} else {
					log.warn(" Error generated for makeGetApiCallCallibration function.");
					log.warn("Retry #" + (3 - retries) + " for makeGetApiCallCallibration function.");
				}
			}

			catch (Exception e) {
				retries--;
				if (retries == 0) {
					response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
					log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error",
							"Exception", "localhost", e.getMessage(), "")));
				} else {
					log.warn(" Error generated for makeGetApiCallCallibration function.");
					log.warn("Retry #" + (2 - retries) + " for makeGetApiCallCallibration function.");
				}

			}
		}
		
		return response;
	}

//	public Response makeGetApiCallCallibrationForOtherDevice(String ipAddress, String frame, String distance)
//			throws SocketException {
//		int id = 1;
//		log.info(" Callibration for second device");
//		ResponseEntity<String> result = null;
//		Response response = null;
//		String otheripAddress = null;
//		try {
//			final String url = "http://" + ipAddress + ":9003/device/ip?ip=" + ipAddress;
//			RestTemplate restTemplate = new RestTemplate();
//			URI uri = new URI(url);
//			result = restTemplate.getForEntity(uri, String.class);
//			if (result.getStatusCode() == HttpStatus.OK) {
//				ObjectMapper objectMapper = new ObjectMapper();
//				JsonNode jsonNode = objectMapper.readTree(result.getBody());
//				// Check if the "data" field is null or empty
//				if (jsonNode.get("data") == null || jsonNode.get("data").isEmpty()) {
//					throw new Exception("No other device IP address found for the given IP: " + ipAddress);
//				}
//
//				otheripAddress = jsonNode.get("data").get(0).get("deviceIpAddress").asText();
//
//				// Check if the "deviceIpAddress" field is null or empty
//				if (otheripAddress == null || otheripAddress.isEmpty()) {
//					throw new Exception("The device IP address is null or empty");
//
//				}
//
//				if (otheripAddress.equals(ipAddress)) {
//					throw new Exception("Ipaddress is same as first Device");
//				}
//				log.info("Second device IP address: " + otheripAddress);
//			} else {
//				log.info("Failed to get IP address. Status code: " + result.getStatusCodeValue());
//			}
//// First API call
//
//			final String url1 = "http://" + otheripAddress + ":8567/stop-fpga-interface";
//			URI uri1 = new URI(url1);
//			result = restTemplate.getForEntity(uri1, String.class);
//			response = new SuccessResponse(result.getBody() + result.getStatusCode(), HttpStatus.OK.value());
//// Second API call
//
//			final String url2 = "http://" + otheripAddress + ":8567/calibrate-air?num=" + frame;
//			URI uri2 = new URI(url2);
//			result = restTemplate.getForEntity(uri2, String.class);
//			response = new SuccessResponse(result.getBody() + result.getStatusCode(), HttpStatus.OK.value());
//			log.info("Api Call for number of frame for second device : " + response);
//// Third API call
//
//			final String url3 = "http://" + otheripAddress + ":8567/process-image?zin=" + distance;
//			URI uri3 = new URI(url3);
//			result = restTemplate.getForEntity(uri3, String.class);
//			response = new SuccessResponse(result.getStatusCode(), HttpStatus.OK.value());
//			log.info("Api Call to process image of second device: " + response);
//
//// Fourth API call
//
//			final String url4 = "http://" + otheripAddress + ":8567/save-air";
//			URI uri4 = new URI(url4);
//			result = restTemplate.getForEntity(uri4, String.class);
//			response = new SuccessResponse(result.getStatusCode(), HttpStatus.OK.value());
//			log.info("Api Call to save air for second device : " + response);
//
//// Fifth API call
//
//			final String url5 = "http://" + otheripAddress + ":8567/start-fpga-interface";
//			URI uri5 = new URI(url5);
//			result = restTemplate.getForEntity(uri5, String.class);
//			response = new SuccessResponse(result.getBody() + result.getStatusCode(), HttpStatus.OK.value());
//
//		} catch (URISyntaxException e) {
//
//			response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
//
//			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error", "Exception",
//					"localhost", e.getMessage(), e.getReason())));
//		} catch (Exception e) {
//			response = new ErrorResponse(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
//			log.error(String.valueOf(new Logs(id++, new Timestamp(System.currentTimeMillis()), "error", "Exception",
//					"localhost", e.getMessage(), "")));
//		}
//
//		return response;
//
//	}
//
}
