package views.questiontypes;

import static org.assertj.core.api.Assertions.assertThat;
import static play.test.Helpers.stubMessagesApi;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import j2html.tags.specialized.DivTag;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.Before;
import org.junit.Test;
import play.i18n.Lang;
import play.i18n.Messages;
import services.LocalizedStrings;
import services.applicant.ApplicantData;
import services.applicant.question.ApplicantQuestion;
import services.question.QuestionOption;
import services.question.types.RadioButtonQuestionDefinition;
import support.QuestionAnswerer;
import views.questiontypes.ApplicantQuestionRendererParams.ErrorDisplayMode;

public class RadioButtonQuestionRendererTest {

  private static final RadioButtonQuestionDefinition QUESTION =
      new RadioButtonQuestionDefinition(
          OptionalLong.of(1),
          "favorite ice cream",
          Optional.empty(),
          "description",
          LocalizedStrings.of(Locale.US, "question?"),
          LocalizedStrings.of(Locale.US, "help text"),
          ImmutableList.of(
              QuestionOption.create(1L, LocalizedStrings.of(Locale.US, "chocolate")),
              QuestionOption.create(2L, LocalizedStrings.of(Locale.US, "peanut butter")),
              QuestionOption.create(3L, LocalizedStrings.of(Locale.US, "vanilla")),
              QuestionOption.create(4L, LocalizedStrings.of(Locale.US, "raspberry"))),
          /* lastModifiedTime= */ Optional.empty());

  private final Messages messages =
      stubMessagesApi().preferred(ImmutableSet.of(Lang.defaultLang()));
  private final ApplicantQuestionRendererParams params =
      ApplicantQuestionRendererParams.builder()
          .setMessages(messages)
          .setErrorDisplayMode(ErrorDisplayMode.HIDE_ERRORS)
          .build();

  private ApplicantData applicantData;
  private ApplicantQuestion question;
  private RadioButtonQuestionRenderer renderer;

  @Before
  public void setup() {
    applicantData = new ApplicantData();
    question = new ApplicantQuestion(QUESTION, applicantData, Optional.empty());
    renderer = new RadioButtonQuestionRenderer(question);
  }

  @Test
  public void render_generatesCorrectInputNames() {
    DivTag result = renderer.render(params);

    assertThat(result.render()).contains("name=\"applicant.favorite_ice_cream.selection\"");
    assertThat(result.render()).contains("value=\"2\"");
  }

  @Test
  public void render_generatesIdsForExplicitLabels() {
    DivTag result = renderer.render(params);

    // Verify we use explicit labels linked to inputs by id for a11y.
    assertThat(result.render()).contains("<label for=");
    assertThat(result.render()).contains("<input id=");
  }

  @Test
  public void render_withExistingAnswer_checksThatOption() {
    QuestionAnswerer.answerSingleSelectQuestion(
        applicantData, question.getContextualizedPath(), 2L);
    DivTag result = renderer.render(params);

    assertThat(result.render())
        .contains(
            "type=\"radio\""
                + " name=\"applicant.favorite_ice_cream.selection\""
                + " value=\"2\" checked");
  }

  @Test
  public void render_withAriaLabels() {
    DivTag result = renderer.render(params);

    assertThat(result.render().matches(".*fieldset aria-describedby=\"[A-Za-z]{8}-description\".*"))
        .isTrue();
  }
}
