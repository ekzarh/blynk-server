package cc.blynk.server.api.http.logic;

import cc.blynk.common.model.messages.protocol.HardwareMessage;
import cc.blynk.common.utils.StringUtils;
import cc.blynk.server.api.http.pojo.EmailPojo;
import cc.blynk.server.api.http.pojo.PushMessagePojo;
import cc.blynk.server.dao.SessionDao;
import cc.blynk.server.dao.UserDao;
import cc.blynk.server.handlers.http.rest.Response;
import cc.blynk.server.model.DashBoard;
import cc.blynk.server.model.HardwareBody;
import cc.blynk.server.model.auth.Session;
import cc.blynk.server.model.auth.User;
import cc.blynk.server.model.enums.PinType;
import cc.blynk.server.model.widgets.Widget;
import cc.blynk.server.model.widgets.notifications.Mail;
import cc.blynk.server.model.widgets.notifications.Notification;
import cc.blynk.server.workers.notifications.BlockingIOProcessor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import static cc.blynk.server.handlers.http.rest.Response.*;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 25.12.15.
 */
@Path("/")
public class HttpAPILogic {

    private static final Logger log = LogManager.getLogger(HttpAPILogic.class);

    private final UserDao userDao;
    private final BlockingIOProcessor blockingIOProcessor;
    private final SessionDao sessionDao;

    public HttpAPILogic(UserDao userDao, SessionDao sessionDao, BlockingIOProcessor blockingIOProcessor) {
        this.userDao = userDao;
        this.blockingIOProcessor = blockingIOProcessor;
        this.sessionDao = sessionDao;
    }

    @GET
    @Path("{token}/pin/{pin}")
    public Response getWidgetPinData(@PathParam("token") String token,
                                     @PathParam("pin") String pinString) {

        User user = userDao.tokenManager.getUserByToken(token);

        if (user == null) {
            log.error("Requested token {} not found.", token);
            return Response.badRequest("Invalid token.");
        }

        Integer dashId = user.getDashIdByToken(token);

        if (dashId == null) {
            log.error("Dash id for token {} not found. User {}", token, user.name);
            return Response.badRequest("Didn't find dash id for token.");
        }

        DashBoard dashBoard = user.profile.getDashById(dashId);

        PinType pinType;
        byte pin;

        try {
            pinType = PinType.getPingType(pinString.charAt(0));
            pin = Byte.parseByte(pinString.substring(1));
        } catch (NumberFormatException e) {
            log.error("Wrong pin format. {}", pinString);
            return Response.badRequest("Wrong pin format.");
        }

        Widget widget = dashBoard.findWidgetByPin(pin, pinType);

        if (widget == null) {
            log.error("Requested pin {} not found. User {}", pinString, user.name);
            return Response.badRequest("Requested pin not exists in app.");
        }

        return ok(widget.getJsonValue());
    }

    @PUT
    @Path("{token}/pin/{pin}")
    @Consumes(value = MediaType.APPLICATION_JSON)
    public Response updateWidgetPinData(@PathParam("token") String token,
                                        @PathParam("pin") String pinString,
                                        String[] pinValues) {

        User user = userDao.tokenManager.getUserByToken(token);

        if (user == null) {
            log.error("Requested token {} not found.", token);
            return Response.badRequest("Invalid token.");
        }

        Integer dashId = user.getDashIdByToken(token);

        if (dashId == null) {
            log.error("Dash id for token {} not found. User {}", token, user.name);
            return Response.badRequest("Didn't find dash id for token.");
        }

        DashBoard dashBoard = user.profile.getDashById(dashId);

        PinType pinType;
        byte pin;

        try {
            pinType = PinType.getPingType(pinString.charAt(0));
            pin = Byte.parseByte(pinString.substring(1));
        } catch (NumberFormatException e) {
            log.error("Wrong pin format. {}", pinString);
            return Response.badRequest("Wrong pin format.");
        }

        Widget widget = dashBoard.findWidgetByPin(pin, pinType);

        if (widget == null) {
            log.error("Requested pin {} not found. User {}", pinString, user.name);
            return Response.badRequest("Requested pin not exists in app.");
        }

        widget.updateIfSame(new HardwareBody(pinType, pin, pinValues));

        String body = widget.makeHardwareBody();

        if (body != null) {
            Session session = sessionDao.userSession.get(user);
            if (session == null) {
                log.error("No session for user {}.", user.name);
                return Response.ok();
            }
            session.sendMessageToHardware(dashId, new HardwareMessage(111, body));
            //todo check for shared apps? to minimize load...
            session.sendToApps(new HardwareMessage(111, dashId + StringUtils.BODY_SEPARATOR_STRING + body));
        }

        return Response.ok();
    }

    @POST
    @Path("{token}/notify")
    @Consumes(value = MediaType.APPLICATION_JSON)
    public Response updateWidgetPinData(@PathParam("token") String token,
                                        PushMessagePojo message) {

        User user = userDao.tokenManager.getUserByToken(token);

        if (user == null) {
            log.error("Requested token {} not found.", token);
            return Response.badRequest("Invalid token.");
        }

        Integer dashId = user.getDashIdByToken(token);

        if (dashId == null) {
            log.error("Dash id for token {} not found. User {}", token, user.name);
            return Response.badRequest("Didn't find dash id for token.");
        }

        if (message == null || Notification.isWrongBody(message.body)) {
            log.error("Notification body is wrong. '{}'", message == null ? "" : message.body);
            return Response.badRequest("Body is empty or larger than 255 chars.");
        }

        DashBoard dash = user.profile.getDashById(dashId);

        if (!dash.isActive) {
            log.error("Project is not active.");
            return Response.badRequest("Project is not active.");
        }

        Notification notification = dash.getWidgetByType(Notification.class);

        if (notification == null || notification.hasNoToken()) {
            log.error("No notification tokens.");
            return Response.badRequest("No notification widget or widget not initialized.");
        }

        log.trace("Sending push for user {}, with message : '{}'.", user.name, message.body);
        blockingIOProcessor.push(user, notification, message.body, 1);

        return Response.ok();
    }

    @POST
    @Path("{token}/email")
    @Consumes(value = MediaType.APPLICATION_JSON)
    public Response updateWidgetPinData(@PathParam("token") String token,
                                        EmailPojo message) {

        User user = userDao.tokenManager.getUserByToken(token);

        if (user == null) {
            log.error("Requested token {} not found.", token);
            return Response.badRequest("Invalid token.");
        }

        Integer dashId = user.getDashIdByToken(token);

        if (dashId == null) {
            log.error("Dash id for token {} not found. User {}", token, user.name);
            return Response.badRequest("Didn't find dash id for token.");
        }

        DashBoard dash = user.profile.getDashById(dashId);

        if (!dash.isActive) {
            log.error("Project is not active.");
            return Response.badRequest("Project is not active.");
        }

        Mail mail = dash.getWidgetByType(Mail.class);

        if (mail == null) {
            log.error("No email widget.");
            return Response.badRequest("No email widget.");
        }

        if (message == null ||
                message.subj == null || message.subj.equals("") ||
                message.to == null || message.to.equals("")) {
            log.error("Email body empty. '{}'", message);
            return Response.badRequest("Email body is wrong. Missing or empty fields 'to', 'subj'.");
        }

        log.trace("Sending Mail for user {}, with message : '{}'.", user.name, message.subj);
        blockingIOProcessor.mail(user, message.to, message.subj, message.title);

        return Response.ok();
    }

}