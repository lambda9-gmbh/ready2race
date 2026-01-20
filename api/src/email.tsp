import "@typespec/http";
import "@typespec/openapi";

using Http;
using OpenAPI;
namespace Ready2Race;

enum EmailLanguage {
  EN;
  DE;
  DA;
}

enum EmailTemplateKey {
  USER_REGISTRATION;
  USER_INVITATION;
  USER_RESET_PASSWORD;
  EVENT_REGISTRATION_CONFIRMATION;
  EVENT_REGISTRATION_INVOICE;
  PARTICIPANT_CHALLENGE_REGISTERED;
  CERTIFICATE_OF_PARTICIPATION_PARTICIPANT;
}

model AssignedEmailDto {
  recipient: string;
  sentAt?: dateTime;
  lastErrorAt?: dateTime;
  lastError?: string;
}

model EmailTemplateDto {
  key: EmailTemplateKey;
  language: EmailLanguage;
  subject: string;
  body: string;
  bodyIsHtml: boolean;
}

enum TransportStrategy {
  SMTP;
  SMTP_TLS;
  SMTPS;
}

model SmtpConfigOverrideDto {
  host: string;
  port: int32;
  username: string;
  password: string;
  smtpStrategy: TransportStrategy;
  fromAddress: string;
  fromName?: string;
  localhost?: string;
  replyTo?: string;
}

@route("/smtp-override")
@tag("Email")
interface EmailRoutes {

  @get
  @operationId("getSmtpConfig")
  op getSmtpConfig(): OK<SmtpConfigOverrideDto> | Unauthorized | Forbidden;

  @put
  @operationId("setSmtpOverride")
  op setSmtpOverride(
    @body request: SmtpConfigOverrideDto;
  ): NoContent | BadRequest | Unauthorized | Forbidden | UnprocessableContent;

  @delete
  @operationId("deleteSmtpOverride")
  op deleteSmtpOverride(): NoContent | Unauthorized | Forbidden;
}
