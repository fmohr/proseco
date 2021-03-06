package de.upb.crc901.proseco.commons.interview;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.upb.crc901.proseco.commons.html.HTMLConstants;
import de.upb.crc901.proseco.commons.html.UIElement;
import de.upb.crc901.proseco.commons.interview.Interview;
import de.upb.crc901.proseco.commons.interview.InterviewFillout;
import de.upb.crc901.proseco.commons.interview.Question;
import de.upb.crc901.proseco.commons.interview.State;
import de.upb.crc901.proseco.commons.util.ListUtil;

@SuppressWarnings("serial")
public class InterviewFillout implements Serializable {
	private Interview interview;
	private Map<String, String> answers; // this is a map from question IDs to answers. Using String instead of Question is ON PURPOSE to ease serialization with Jackson!
	private State currentState;

	public InterviewFillout() {
	}

	public InterviewFillout(final Interview interview) {
		super();
		this.interview = interview;
		this.answers = new HashMap<>();
		this.currentState = interview.getStates().get(0);
	}

	public InterviewFillout(final Interview interview, final Map<String, String> answers, final State currentState) {
		super();
		this.interview = interview;
		this.answers = answers;
		this.currentState = currentState;
	}

	/**
	 * This constructor automatically activates the first state with an unanswered question
	 *
	 * @param interview
	 * @param answers
	 */
	public InterviewFillout(final Interview interview, final Map<String, String> answers) {
		super();
		this.interview = interview;
		this.answers = answers;
		for (State s : interview.getStates()) {
			List<Question> questions = s.getQuestions();
			if (ListUtil.isNotEmpty(questions)) {
				for (Question q : questions) {
					if (!answers.containsKey(q.getId())) {
						this.currentState = s;
						return;
					}
				}
			}
		}
		this.currentState = interview.getStates().get(0);
	}

	public Interview getInterview() {
		return this.interview;
	}

	public Map<String, String> getAnswers() {
		return this.answers;
	}

	public String getAnswer(final Question q) {
		return this.answers.get(q.getId());
	}

	public String getAnswer(final String questionId) {
		return this.answers.get(questionId);
	}

	/**
	 * return currentState
	 *
	 * @return
	 */
	public State getCurrentState() {
		return this.currentState;
	}

	public boolean allQuestionsInCurrentStateAnswered() {
		// if current state has unanswered questions return current state
		List<Question> questions = this.currentState.getQuestions();
		if (ListUtil.isNotEmpty(questions)) {
			for (Question q : questions) {
				if (!this.answers.containsKey(q.getId())) {
					return false;
				}
			}
		}
		return true;
	}

	// public void nextState() throws NextStateNotFoundException {
	// String nextStateName = AnswerInterpreter.findNextState(this, currentState);
	// if (nextStateName != null) {
	// assert states.contains(stateMap.get(nextStateName)) : "Switching to state " + nextStateName + " that is not in the list of states!";
	// currentState = stateMap.get(nextStateName);
	// } // else there is no next step i.e. last step
	// }
	//
	// public void prevState() {
	// String nextStateName = stateMap.get(currentState.getName()).getTransition().get("prev");
	// if (nextStateName != null) {
	// currentState = stateMap.get(nextStateName);
	// } // else there is no next step i.e. last step
	// }

	/**
	 * Generates concrete HTML element from the UI Elements of the questions to make up the form
	 *
	 * @return
	 */
	public String getHTMLOfOpenQuestionsInState(final State s) {
		StringBuilder htmlElement = new StringBuilder();

		for (Question q : s.getQuestions()) {
			if (!this.answers.containsKey(q.getId())) {
				String formQuestion = q.getContent();
				if (formQuestion != null) {
					htmlElement.append(HTMLConstants.LINE_BREAK).append("<h1>" + formQuestion + "</h1>").append(HTMLConstants.LINE_BREAK);
				}
				UIElement formUiElement = q.getUiElement();
				if (formUiElement != null) {
					htmlElement.append(formUiElement.toHTML()).append(HTMLConstants.LINE_BREAK).append("\n");
				}
			}
		}

		return htmlElement.toString();
	}

	@JsonIgnore
	public String getHTMLOfOpenQuestionsInCurrentState() {
		return this.getHTMLOfOpenQuestionsInState(this.currentState);
	}

	@JsonIgnore
	public String getHTMLOfAllOpenQuestions() {
		StringBuilder html = new StringBuilder();
		for (State s : this.interview.getStates()) {
			html.append(this.getHTMLOfOpenQuestionsInState(s)).append("\n");
		}
		return html.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((this.answers == null) ? 0 : this.answers.hashCode());
		result = prime * result + ((this.currentState == null) ? 0 : this.currentState.hashCode());
		result = prime * result + ((this.interview == null) ? 0 : this.interview.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (this.getClass() != obj.getClass()) {
			return false;
		}
		InterviewFillout other = (InterviewFillout) obj;
		if (this.answers == null) {
			if (other.answers != null) {
				return false;
			}
		} else if (!this.answers.equals(other.answers)) {
			return false;
		}
		if (this.currentState == null) {
			if (other.currentState != null) {
				return false;
			}
		} else if (!this.currentState.equals(other.currentState)) {
			return false;
		}
		if (this.interview == null) {
			if (other.interview != null) {
				return false;
			}
		} else if (!this.interview.equals(other.interview)) {
			return false;
		}
		return true;
	}

	public void setInterview(final Interview interview) {
		if (this.interview != null) {
			throw new IllegalStateException("Cannot modify interview if it is already set!");
		}
		if (interview == null) {
			throw new IllegalStateException("Cannot set interview to NULL!");
		}
		this.interview = interview;
	}

	public void setAnswers(final Map<String, String> answers) {
		if (this.answers != null) {
			throw new IllegalStateException("Cannot modify answers if they are already set!");
		}
		if (answers == null) {
			throw new IllegalStateException("Cannot set answer to NULL!");
		}
		this.answers = answers;
	}

	public void setCurrentState(final State currentState) {
		if (this.currentState != null) {
			throw new IllegalStateException("Cannot modify state if it is already set!");
		}
		if (currentState == null) {
			throw new IllegalStateException("Cannot set state to NULL!");
		}
		this.currentState = currentState;
	}

}
