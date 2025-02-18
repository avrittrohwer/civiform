package controllers.admin;

import static com.google.common.base.Preconditions.checkNotNull;
import static views.admin.programs.ProgramApplicationView.CURRENT_STATUS;
import static views.admin.programs.ProgramApplicationView.NEW_STATUS;
import static views.admin.programs.ProgramApplicationView.NOTE;
import static views.admin.programs.ProgramApplicationView.SEND_EMAIL;

import annotations.BindingAnnotations.Now;
import auth.Authorizers;
import auth.ProfileUtils;
import com.google.common.collect.ImmutableList;
import com.google.inject.Provider;
import com.itextpdf.text.DocumentException;
import controllers.BadRequestException;
import controllers.CiviFormController;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import javax.inject.Inject;
import models.Application;
import org.pac4j.play.java.Secure;
import play.data.FormFactory;
import play.i18n.Messages;
import play.i18n.MessagesApi;
import play.libs.F;
import play.mvc.Http;
import play.mvc.Result;
import repository.SubmittedApplicationFilter;
import repository.TimeFilter;
import services.DateConverter;
import services.IdentifierBasedPaginationSpec;
import services.PageNumberBasedPaginationSpec;
import services.PaginationResult;
import services.UrlUtils;
import services.applicant.AnswerData;
import services.applicant.ApplicantService;
import services.applicant.Block;
import services.applicant.ReadOnlyApplicantProgramService;
import services.application.ApplicationEventDetails;
import services.applications.AccountHasNoEmailException;
import services.applications.ProgramAdminApplicationService;
import services.applications.StatusEmailNotFoundException;
import services.export.CsvExporterService;
import services.export.JsonExporter;
import services.export.PdfExporter;
import services.program.ProgramDefinition;
import services.program.ProgramNotFoundException;
import services.program.ProgramService;
import services.program.StatusDefinitions;
import services.program.StatusNotFoundException;
import views.ApplicantUtils;
import views.admin.programs.ProgramApplicationListView;
import views.admin.programs.ProgramApplicationListView.RenderFilterParams;
import views.admin.programs.ProgramApplicationView;

/** Controller for admins viewing applications to programs. */
public final class AdminApplicationController extends CiviFormController {
  private static final int PAGE_SIZE = 10;

  private static final String REDIRECT_URI_KEY = "redirectUri";

  private final ApplicantService applicantService;
  private final ProgramAdminApplicationService programAdminApplicationService;
  private final ProgramApplicationListView applicationListView;
  private final ProgramApplicationView applicationView;
  private final ProgramService programService;
  private final CsvExporterService exporterService;
  private final FormFactory formFactory;
  private final JsonExporter jsonExporter;
  private final PdfExporter pdfExporter;
  private final ProfileUtils profileUtils;
  private final Provider<LocalDateTime> nowProvider;
  private final MessagesApi messagesApi;
  private final DateConverter dateConverter;

  @Inject
  public AdminApplicationController(
      ProgramService programService,
      ApplicantService applicantService,
      CsvExporterService csvExporterService,
      FormFactory formFactory,
      JsonExporter jsonExporter,
      PdfExporter pdfExporter,
      ProgramApplicationListView applicationListView,
      ProgramApplicationView applicationView,
      ProgramAdminApplicationService programAdminApplicationService,
      ProfileUtils profileUtils,
      MessagesApi messagesApi,
      DateConverter dateConverter,
      @Now Provider<LocalDateTime> nowProvider) {
    this.programService = checkNotNull(programService);
    this.applicantService = checkNotNull(applicantService);
    this.applicationListView = checkNotNull(applicationListView);
    this.profileUtils = checkNotNull(profileUtils);
    this.applicationView = checkNotNull(applicationView);
    this.programAdminApplicationService = checkNotNull(programAdminApplicationService);
    this.nowProvider = checkNotNull(nowProvider);
    this.exporterService = checkNotNull(csvExporterService);
    this.formFactory = checkNotNull(formFactory);
    this.jsonExporter = checkNotNull(jsonExporter);
    this.pdfExporter = checkNotNull(pdfExporter);
    this.messagesApi = checkNotNull(messagesApi);
    this.dateConverter = checkNotNull(dateConverter);
  }

  /** Download a JSON file containing all applications to all versions of the specified program. */
  @Secure(authorizers = Authorizers.Labels.ANY_ADMIN)
  public Result downloadAllJson(
      Http.Request request,
      long programId,
      Optional<String> search,
      Optional<String> fromDate,
      Optional<String> untilDate,
      Optional<String> applicationStatus,
      Optional<String> ignoreFilters)
      throws ProgramNotFoundException {
    final ProgramDefinition program;

    try {
      program = programService.getProgramDefinition(programId);
      checkProgramAdminAuthorization(profileUtils, request, program.adminName()).join();
    } catch (CompletionException | NoSuchElementException e) {
      return unauthorized();
    }

    boolean shouldApplyFilters = ignoreFilters.orElse("").isEmpty();
    SubmittedApplicationFilter filters = SubmittedApplicationFilter.EMPTY;
    if (shouldApplyFilters) {
      filters =
          SubmittedApplicationFilter.builder()
              .setSearchNameFragment(search)
              .setSubmitTimeFilter(
                  TimeFilter.builder()
                      .setFromTime(parseDateFromQuery(dateConverter, fromDate))
                      .setUntilTime(parseDateFromQuery(dateConverter, untilDate))
                      .build())
              .setApplicationStatus(applicationStatus)
              .build();
    }

    String filename = String.format("%s-%s.json", program.adminName(), nowProvider.get());
    String json =
        jsonExporter
            .export(program, IdentifierBasedPaginationSpec.MAX_PAGE_SIZE_SPEC_LONG, filters)
            .getLeft();

    return ok(json)
        .as(Http.MimeTypes.JSON)
        .withHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", filename));
  }

  /** Download a CSV file containing all applications to all versions of the specified program. */
  @Secure(authorizers = Authorizers.Labels.ANY_ADMIN)
  public Result downloadAll(
      Http.Request request,
      long programId,
      Optional<String> search,
      Optional<String> fromDate,
      Optional<String> untilDate,
      Optional<String> applicationStatus,
      Optional<String> ignoreFilters)
      throws ProgramNotFoundException {
    boolean shouldApplyFilters = ignoreFilters.orElse("").isEmpty();
    try {
      SubmittedApplicationFilter filters = SubmittedApplicationFilter.EMPTY;
      if (shouldApplyFilters) {
        filters =
            SubmittedApplicationFilter.builder()
                .setSearchNameFragment(search)
                .setSubmitTimeFilter(
                    TimeFilter.builder()
                        .setFromTime(parseDateFromQuery(dateConverter, fromDate))
                        .setUntilTime(parseDateFromQuery(dateConverter, untilDate))
                        .build())
                .setApplicationStatus(applicationStatus)
                .build();
      }
      ProgramDefinition program = programService.getProgramDefinition(programId);
      checkProgramAdminAuthorization(profileUtils, request, program.adminName()).join();
      String filename = String.format("%s-%s.csv", program.adminName(), nowProvider.get());
      String csv = exporterService.getProgramAllVersionsCsv(programId, filters);
      return ok(csv)
          .as(Http.MimeTypes.BINARY)
          .withHeader(
              "Content-Disposition", String.format("attachment; filename=\"%s\"", filename));
    } catch (CompletionException | NoSuchElementException e) {
      return unauthorized();
    }
  }

  /**
   * Download a CSV file containing all applications to the specified program version. This was the
   * original behavior for the program admin CSV download but is currently unused as of 10/13/2021.
   */
  @Secure(authorizers = Authorizers.Labels.ANY_ADMIN)
  public Result downloadSingleVersion(Http.Request request, long programId)
      throws ProgramNotFoundException {
    try {
      ProgramDefinition program = programService.getProgramDefinition(programId);
      checkProgramAdminAuthorization(profileUtils, request, program.adminName()).join();
      String filename = String.format("%s-%s.csv", program.adminName(), nowProvider.get());
      String csv = exporterService.getProgramCsv(programId);
      return ok(csv)
          .as(Http.MimeTypes.BINARY)
          .withHeader(
              "Content-Disposition", String.format("attachment; filename=\"%s\"", filename));
    } catch (CompletionException | NoSuchElementException e) {
      return unauthorized();
    }
  }

  /**
   * Parses a date from a raw query string (e.g. 2022-01-02) and returns an instant representing the
   * start of that date in the time zone configured for the server deployment.
   */
  private Optional<Instant> parseDateFromQuery(
      DateConverter dateConverter, Optional<String> maybeQueryParam) {
    return maybeQueryParam
        .filter(s -> !s.isBlank())
        .map(
            s -> {
              try {
                return dateConverter.parseIso8601DateToStartOfDateInstant(s);
              } catch (DateTimeParseException e) {
                throw new BadRequestException("Malformed query param");
              }
            });
  }

  /**
   * Download a CSV file containing demographics information of the current live version.
   * Demographics information is collected from answers to a collection of questions specially
   * marked by CiviForm admins.
   */
  @Secure(authorizers = Authorizers.Labels.CIVIFORM_ADMIN)
  public Result downloadDemographics(
      Http.Request request, Optional<String> fromDate, Optional<String> untilDate) {
    TimeFilter submitTimeFilter =
        TimeFilter.builder()
            .setFromTime(parseDateFromQuery(dateConverter, fromDate))
            .setUntilTime(parseDateFromQuery(dateConverter, untilDate))
            .build();
    String filename = String.format("demographics-%s.csv", nowProvider.get());
    String csv = exporterService.getDemographicsCsv(submitTimeFilter);
    return ok(csv)
        .as(Http.MimeTypes.BINARY)
        .withHeader("Content-Disposition", String.format("attachment; filename=\"%s\"", filename));
  }

  /** Download a PDF file of the application to the program. */
  @Secure(authorizers = Authorizers.Labels.ANY_ADMIN)
  public Result download(Http.Request request, long programId, long applicationId)
      throws ProgramNotFoundException {
    ProgramDefinition program = programService.getProgramDefinition(programId);
    try {
      checkProgramAdminAuthorization(profileUtils, request, program.adminName()).join();
    } catch (CompletionException | NoSuchElementException e) {
      return unauthorized();
    }

    Optional<Application> applicationMaybe =
        programAdminApplicationService.getApplication(applicationId, program);
    if (applicationMaybe.isEmpty()) {
      return notFound(String.format("Application %d does not exist.", applicationId));
    }
    Application application = applicationMaybe.get();

    PdfExporter.InMemoryPdf pdf;
    try {
      pdf = pdfExporter.export(application);
    } catch (DocumentException | IOException e) {
      throw new RuntimeException(e);
    }
    return ok(pdf.getByteArray())
        .as("application/pdf")
        .withHeader(
            "Content-Disposition", String.format("attachment; filename=\"%s\"", pdf.getFileName()));
  }

  /** Return a HTML page displaying the summary of the specified application. */
  @Secure(authorizers = Authorizers.Labels.ANY_ADMIN)
  public Result show(Http.Request request, long programId, long applicationId)
      throws ProgramNotFoundException {
    ProgramDefinition program = programService.getProgramDefinition(programId);
    String programName = program.adminName();

    try {
      checkProgramAdminAuthorization(profileUtils, request, programName).join();
    } catch (CompletionException | NoSuchElementException e) {
      return unauthorized();
    }

    Optional<Application> applicationMaybe =
        programAdminApplicationService.getApplication(applicationId, program);
    if (applicationMaybe.isEmpty()) {
      return notFound(String.format("Application %d does not exist.", applicationId));
    }
    Application application = applicationMaybe.get();

    Messages messages = messagesApi.preferred(request);
    String applicantNameWithApplicationId =
        String.format(
            "%s (%d)",
            ApplicantUtils.getApplicantName(
                application.getApplicantData().getApplicantName(), messages),
            application.id);

    ReadOnlyApplicantProgramService roApplicantService =
        applicantService
            .getReadOnlyApplicantProgramService(application)
            .toCompletableFuture()
            .join();
    ImmutableList<Block> blocks = roApplicantService.getAllActiveBlocks();
    ImmutableList<AnswerData> answers = roApplicantService.getSummaryData();
    Optional<String> noteMaybe = programAdminApplicationService.getNote(application);

    return ok(
        applicationView.render(
            programId,
            programName,
            application,
            applicantNameWithApplicationId,
            blocks,
            answers,
            program.statusDefinitions(),
            noteMaybe,
            program.hasEligibilityEnabled(),
            request));
  }

  /**
   * Updates the status for the associated application and redirects to the summary page for the
   * application.
   */
  @Secure(authorizers = Authorizers.Labels.ANY_ADMIN)
  public Result updateStatus(Http.Request request, long programId, long applicationId)
      throws ProgramNotFoundException, StatusEmailNotFoundException, StatusNotFoundException,
          AccountHasNoEmailException {
    ProgramDefinition program = programService.getProgramDefinition(programId);
    String programName = program.adminName();

    try {
      checkProgramAdminAuthorization(profileUtils, request, programName).join();
    } catch (CompletionException | NoSuchElementException e) {
      return unauthorized();
    }

    Optional<Application> applicationMaybe =
        programAdminApplicationService.getApplication(applicationId, program);
    if (applicationMaybe.isEmpty()) {
      return notFound(String.format("Application %d does not exist.", applicationId));
    }
    Application application = applicationMaybe.get();

    Map<String, String> formData = formFactory.form().bindFromRequest(request).rawData();
    Optional<String> maybeCurrentStatus = Optional.ofNullable(formData.get(CURRENT_STATUS));
    Optional<String> maybeNewStatus = Optional.ofNullable(formData.get(NEW_STATUS));
    Optional<String> maybeSendEmail = Optional.ofNullable(formData.get(SEND_EMAIL));
    Optional<String> maybeRedirectUri = Optional.ofNullable(formData.get(REDIRECT_URI_KEY));
    if (maybeCurrentStatus.isEmpty()) {
      return badRequest(String.format("The %s field is not present", CURRENT_STATUS));
    }
    if (maybeNewStatus.isEmpty()) {
      return badRequest(String.format("The %s field is not present", NEW_STATUS));
    }
    if (maybeSendEmail.isEmpty()) {
      return badRequest(String.format("The %s field is not present", SEND_EMAIL));
    }
    if (maybeRedirectUri.isEmpty()) {
      return badRequest(String.format("The %s field is not present", REDIRECT_URI_KEY));
    }
    // Verify the UI is changing from the actual current status to detect an out of date UI.
    if (application.getLatestStatus().isPresent()) {
      if (!application.getLatestStatus().get().equals(maybeCurrentStatus.get())) {
        // Only allow relative URLs to ensure that we redirect to the same domain.
        String redirectUrl = UrlUtils.checkIsRelativeUrl(maybeRedirectUri.orElse(""));
        return redirect(redirectUrl)
            .flashing(
                "error",
                "The application state has changed since the page was loaded. Please reload and"
                    + " try again.");
      }
    } else if (!maybeCurrentStatus.get().isBlank()) {
      return badRequest(
          String.format("The %s field should be empty as there is no status set", CURRENT_STATUS));
    }
    // Save the new data.
    String newStatus = maybeNewStatus.get();
    final boolean sendEmail;
    if (maybeSendEmail.get().isBlank()) {
      sendEmail = false;
    } else if (maybeSendEmail.get().equals("on")) {
      sendEmail = true;
    } else {
      return badRequest(String.format("%s value is invalid: %s", SEND_EMAIL, maybeSendEmail.get()));
    }

    programAdminApplicationService.setStatus(
        application,
        ApplicationEventDetails.StatusEvent.builder()
            .setStatusText(newStatus)
            .setEmailSent(sendEmail)
            .build(),
        profileUtils.currentUserProfile(request).get().getAccount().join());
    // Only allow relative URLs to ensure that we redirect to the same domain.
    String redirectUrl = UrlUtils.checkIsRelativeUrl(maybeRedirectUri.orElse(""));
    return redirect(redirectUrl).flashing("success", "Application status updated");
  }

  /**
   * Edits the note for the associated application and redirects to the summary page for the
   * application.
   */
  @Secure(authorizers = Authorizers.Labels.ANY_ADMIN)
  public Result updateNote(Http.Request request, long programId, long applicationId)
      throws ProgramNotFoundException {
    ProgramDefinition program = programService.getProgramDefinition(programId);
    String programName = program.adminName();

    try {
      checkProgramAdminAuthorization(profileUtils, request, programName).join();
    } catch (CompletionException | NoSuchElementException e) {
      return unauthorized();
    }

    Optional<Application> applicationMaybe =
        programAdminApplicationService.getApplication(applicationId, program);
    if (applicationMaybe.isEmpty()) {
      return notFound(String.format("Application %d does not exist.", applicationId));
    }
    Application application = applicationMaybe.get();

    Map<String, String> formData = formFactory.form().bindFromRequest(request).rawData();
    Optional<String> maybeNote = Optional.ofNullable(formData.get(NOTE));
    Optional<String> maybeRedirectUri = Optional.ofNullable(formData.get(REDIRECT_URI_KEY));
    if (maybeNote.isEmpty()) {
      return badRequest("A note is not present.");
    }
    String note = maybeNote.get();
    if (maybeRedirectUri.isEmpty()) {
      return badRequest("A redirect URI is not present");
    }

    programAdminApplicationService.setNote(
        application,
        ApplicationEventDetails.NoteEvent.create(note),
        profileUtils.currentUserProfile(request).get().getAccount().join());

    // Only allow relative URLs to ensure that we redirect to the same domain.
    String redirectUrl = UrlUtils.checkIsRelativeUrl(maybeRedirectUri.orElse(""));
    return redirect(redirectUrl).flashing("success", "Application note updated");
  }

  /** Return a paginated HTML page displaying (part of) all applications to the program. */
  @Secure(authorizers = Authorizers.Labels.ANY_ADMIN)
  public Result index(
      Http.Request request,
      long programId,
      Optional<String> search,
      Optional<Integer> page,
      Optional<String> fromDate,
      Optional<String> untilDate,
      Optional<String> applicationStatus,
      Optional<String> selectedApplicationUri)
      throws ProgramNotFoundException {
    if (page.isEmpty()) {
      return redirect(
          routes.AdminApplicationController.index(
              programId,
              search,
              Optional.of(1),
              fromDate,
              untilDate,
              applicationStatus,
              selectedApplicationUri));
    }

    SubmittedApplicationFilter filters =
        SubmittedApplicationFilter.builder()
            .setSearchNameFragment(search)
            .setSubmitTimeFilter(
                TimeFilter.builder()
                    .setFromTime(parseDateFromQuery(dateConverter, fromDate))
                    .setUntilTime(parseDateFromQuery(dateConverter, untilDate))
                    .build())
            .setApplicationStatus(applicationStatus)
            .build();

    final ProgramDefinition program;
    try {
      program = programService.getProgramDefinition(programId);
      checkProgramAdminAuthorization(profileUtils, request, program.adminName()).join();
    } catch (CompletionException | NoSuchElementException e) {
      return unauthorized();
    }

    var paginationSpec = new PageNumberBasedPaginationSpec(PAGE_SIZE, page.orElse(1));
    PaginationResult<Application> applications =
        programService.getSubmittedProgramApplicationsAllVersions(
            programId, F.Either.Right(paginationSpec), filters);

    return ok(
        applicationListView.render(
            request,
            program,
            getAllApplicationStatusesForProgram(program.id()),
            paginationSpec,
            applications,
            RenderFilterParams.builder()
                .setSearch(search)
                .setFromDate(fromDate)
                .setUntilDate(untilDate)
                .setSelectedApplicationStatus(applicationStatus)
                .build(),
            selectedApplicationUri));
  }

  private ImmutableList<String> getAllApplicationStatusesForProgram(long programId) {
    return programService.getAllProgramDefinitionVersions(programId).stream()
        .map(pdef -> pdef.statusDefinitions().getStatuses())
        .flatMap(ImmutableList::stream)
        .map(StatusDefinitions.Status::statusText)
        .distinct()
        .sorted()
        .collect(ImmutableList.toImmutableList());
  }
}
