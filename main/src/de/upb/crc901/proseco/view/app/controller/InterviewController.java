package de.upb.crc901.proseco.view.app.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.aeonbits.owner.ConfigCache;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.thymeleaf.util.StringUtils;

import de.upb.crc901.proseco.core.PROSECOConfig;
import de.upb.crc901.proseco.core.composition.CompositionAlgorithm;
import de.upb.crc901.proseco.core.composition.PROSECOProcessEnvironment;
import de.upb.crc901.proseco.core.interview.InterviewFillout;
import de.upb.crc901.proseco.core.interview.Question;
import de.upb.crc901.proseco.view.app.model.InterviewDTO;
import de.upb.crc901.proseco.view.core.NextStateNotFoundException;
import de.upb.crc901.proseco.view.core.Parser;
import de.upb.crc901.proseco.view.util.ListUtil;
import de.upb.crc901.proseco.view.util.SerializationUtil;

/**
 * Interview Controller for web application
 * 
 * 
 * @author kadirayk
 *
 */
@Controller
public class InterviewController {

	private static final Logger logger = LoggerFactory.getLogger(InterviewController.class);
	private static final PROSECOConfig config = ConfigCache.getOrCreate(PROSECOConfig.class);
	private static final String INIT_TEMPLATE = "initiator";
	private static final String RESULT_TEMPLATE = "result";
	private static final String ERROR_TEMPLATE = "error";

	/**
	 * Displays Interview initiator. Interview initiator is the step where the user inputs the required keywords for corresponding prototype to be found.
	 * 
	 * @param model
	 * @return
	 */
	@GetMapping("/init")
	public String init(Model model) {
		model.addAttribute("interviewDTO", new InterviewDTO());
		return INIT_TEMPLATE;
	}

	/**
	 * Initiates interview process and decides prototype according to given information
	 * 
	 * @param init
	 * @return
	 * @throws NextStateNotFoundException
	 */
	@PostMapping("/init")
	public String initSubmit(@ModelAttribute InterviewDTO interviewDTO) throws NextStateNotFoundException {

		/* determine prototype name */
		String prototypeName = null;
		if (StringUtils.containsIgnoreCase(interviewDTO.getContent(), "image classification", Locale.ENGLISH)
				|| StringUtils.containsIgnoreCase(interviewDTO.getContent(), "ic", Locale.ENGLISH)) {
			prototypeName = "imageclassification";
		} else if (StringUtils.containsIgnoreCase(interviewDTO.getContent(), "play a game", Locale.ENGLISH)
				|| StringUtils.containsIgnoreCase(interviewDTO.getContent(), "game", Locale.ENGLISH)) {
			prototypeName = "game";

		} else if (StringUtils.containsIgnoreCase(interviewDTO.getContent(), "automl", Locale.ENGLISH)) {
			prototypeName = "automl";
		} else {
			return ERROR_TEMPLATE;
		}

		/* create a new PROSECO service construction process and retrieve the interview */
		try {
			String id = createConstructionProcess(prototypeName);
			PROSECOProcessEnvironment env = getEnvironment(prototypeName + "-" + id);
			System.out.println(env.getPrototypeConfig());
			File file = new File(env.getInterviewDirectory().getAbsolutePath() + File.separator + "interview.yaml");
			Parser parser = new Parser();
			interviewDTO.setInterviewFillout(new InterviewFillout(parser.initializeInterviewFromConfig(file)));
			interviewDTO.setProcessId(id);
		} catch (IOException e) {
			System.err.println("Error in creating a construction process for prototype " + prototypeName + ". The exception is as follows:");
			e.printStackTrace();
		}
		saveInterviewState(interviewDTO);
		return RESULT_TEMPLATE;
	}

	/**
	 * Http Get method for /interview/{id} to display the current state of the interview with the given {id}
	 * 
	 * @param init
	 * @return
	 * @throws NextStateNotFoundException
	 */
	@GetMapping("/interview/{id}")
	public String next(@PathVariable("id") String id, @ModelAttribute InterviewDTO interviewDTO) throws NextStateNotFoundException {
		populateInterviewDTO(interviewDTO, id);
		return RESULT_TEMPLATE;
	}

	@GetMapping("/prev")
	public String prev(@ModelAttribute InterviewDTO init) {
		// if (memorizedInterview != null) {
		// memorizedInterview.prevState();
		// init.setInterview(memorizedInterview);
		// }
		return RESULT_TEMPLATE;
	}

	/**
	 * Http Post method for /interview/{id} to post form values and continue to the next step
	 * 
	 * @param interviewDTO
	 * @param response
	 *            is any string value that is filled in the form
	 * @param file
	 *            is any file that is uploaded via the form
	 * @return
	 * @throws NextStateNotFoundException
	 */
	@PostMapping("/interview/{id}")
	public String nextPost(@PathVariable("id") String id, @ModelAttribute InterviewDTO interviewDTO, @RequestParam(required = false, name = "response") String response,
			@RequestParam(required = false, name = "file") MultipartFile file) throws NextStateNotFoundException {
		
		/* retrieve the interview state */
		logger.info("Receiving response {} and file {} for process id {}. Interview: {}", response, file, id, interviewDTO);
		populateInterviewDTO(interviewDTO, id);
		logger.info("Receiving response {} and file {} for process id {}. Interview: {}", response, file, id, interviewDTO);
		InterviewFillout memorizedInterviewFillout = interviewDTO.getInterviewFillout();
		PROSECOProcessEnvironment env = getEnvironment(memorizedInterviewFillout.getInterview().getPrototypeName() + "-" + id);
		
		Map<String,String> updatedAnswers = new HashMap<>(memorizedInterviewFillout.getAnswers());

		// if it is final state run PrototypeBasedComposer with interview inputs
		if (memorizedInterviewFillout.getCurrentState().getTransition() == null || memorizedInterviewFillout.getCurrentState().getTransition().isEmpty()) {
			Runnable task = () -> {
				try {
					CompositionAlgorithm pc = new CompositionAlgorithm(env, 100);
					pc.run();
				} catch (Exception e) {
					e.printStackTrace();
				}
			};
			new Thread(task).start();
			interviewDTO.setShowConsole(true);
			return RESULT_TEMPLATE;
		}

		// if a file is uploaded save the file to prototype's interview directory set the reference of file (file path) as answer to the respected question in the interview
		if (file != null && !file.isEmpty()) {
			try {
				List<Question> questions = memorizedInterviewFillout.getCurrentState().getQuestions();
				if (ListUtil.isNotEmpty(questions)) {
					for (Question q : questions) {
						if ("file".equals(q.getUiElement().getAttributes().get("type"))) {
							byte[] bytes = file.getBytes();
							Path path = Paths.get(env.getInterviewResourcesDirectory() + File.separator + q.getId());
							Files.write(path, bytes);
							updatedAnswers.put(q.getId(), path.toFile().getName());
						}
					}
				}

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		// if any string response is given set the responses as answer to the respected interview question
		if (response != null && !StringUtils.isEmpty(response)) {
			List<String> answers = Arrays.asList(response.split(","));
			List<Question> questions = memorizedInterviewFillout.getCurrentState().getQuestions();
			if (ListUtil.isNotEmpty(questions)) {
				int i = 0;
				for (Question q : questions) {
					String answerToThisQuestion = answers.get(i);
					if (!StringUtils.isEmpty(answerToThisQuestion)) {
						logger.warn("Question \"{}\"has already been answered.", q);
						continue;
					}
					if ("file".equals(q.getUiElement().getAttributes().get("type"))) {
						logger.warn("Cannot process file fields in standard process");
						continue;
					}
					if (i < answers.size()) {
						updatedAnswers.put(q.getId(), answerToThisQuestion);
						i++;
					}
				}
			}
		}
//		logger.info("Interview state after having processed the answers is {}. Questions: {}", interviewDTO.getInterviewFillout().getCurrentState(), interviewDTO.getInterviewFillout().getCurrentState().getQuestions().stream()
//				.map(q -> "\n\t" + q.getContent() + "(" + q + "): " + q.getAnswer()).collect(Collectors.joining()));
		// update current interview state (to the first state with an unanswered question) and save it
		interviewDTO.setInterviewFillout(new InterviewFillout(memorizedInterviewFillout.getInterview(), updatedAnswers));
		saveInterviewState(interviewDTO);
//		logger.info("Interview state after having it saved is {}. Questions: {}", interviewDTO.getInterviewFillout().getCurrentState(), interviewDTO.getInterviewFillout().getCurrentState().getQuestions().stream()
//				.map(q -> "\n\t" + q.getContent() + "(" + q + "): " + q.getAnswer()).collect(Collectors.joining()));
//		interviewDTO.getInterviewFillout().getStates().forEach(
//				s -> logger.info("Saving interview state {} with questions:{}", s, s.getQuestions().stream().map(q -> "\n\t" + q.getContent() + "(" + q + "): " + q.getAnswer()).collect(Collectors.joining())));
		return RESULT_TEMPLATE;
	}

	/**
	 * Creates a new PROSECO service construction process for a given prototype. The prototype skeleton is copied for the new process.
	 * 
	 * @return id The id for the newly created process
	 * @throws IOException
	 */
	private String createConstructionProcess(String prototypeName) throws IOException {
		String id = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
		PROSECOProcessEnvironment env = getEnvironment(prototypeName + "-" + id);
		FileUtils.copyDirectory(env.getPrototypeDirectory(), env.getProcessDirectory());
		return id;
	}

	/**
	 * Finds interview of the prototype with the given ID
	 * 
	 * @param id
	 * @return
	 */
	private void populateInterviewDTO(InterviewDTO interviewDTO, String id) {
		PROSECOProcessEnvironment env = getEnvironment(Util.getPrototypeNameForProcessId(config, id));
		InterviewFillout interview = SerializationUtil.readAsJSON(env.getInterviewStateDirectory());
		interviewDTO.setInterviewFillout(interview);
		interviewDTO.setProcessId(id);
	}

	/**
	 * saves interview state on current prototype instance's directory
	 * 
	 * @param interviewDTO
	 */
	private void saveInterviewState(InterviewDTO interviewDTO) {
		PROSECOProcessEnvironment env = getEnvironment(interviewDTO.getInterviewFillout().getInterview().getPrototypeName() + "-" + interviewDTO.getProcessId());
		SerializationUtil.writeAsJSON(env.getInterviewStateDirectory(), interviewDTO.getInterviewFillout());
	}

	private PROSECOProcessEnvironment getEnvironment(String processId) {
		try {
			return new PROSECOProcessEnvironment(config, processId);
		} catch (Exception e) {
			throw new RuntimeException("Could not create an environment object for process id " + processId, e);
		}
	}
}