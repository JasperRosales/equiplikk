package web.main;

import src.controller.AuthController;
import src.controller.BorrowRequestController;
import src.controller.EquipmentController;
import src.controller.NotificationController;
import src.service.AuthService;
import src.service.BorrowRequestService;
import src.service.EquipmentService;
import src.service.NotificationService;
import web.security.SessionManager;
import web.service.VenueService;

import java.nio.file.Path;

/** One shared instance of the existing desktop controllers for all HTTP requests. */
public final class AppServices {

    public final AuthController auth = new AuthController(new AuthService());
    public final BorrowRequestController requests =
            new BorrowRequestController(new BorrowRequestService());
    public final EquipmentController equipment = new EquipmentController(new EquipmentService());
    public final NotificationController notifications =
            new NotificationController(new NotificationService());

    public final SessionManager sessions;
    public final VenueService venues;

    public AppServices(Path webHome, int sessionMinutes) {
        this.sessions = new SessionManager(sessionMinutes);
        this.venues = new VenueService(webHome);
    }
}
