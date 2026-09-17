package delivery.portal.service;

import delivery.identity.WorkspaceDirectory;
import delivery.portal.DeliveryPortalProperties;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class WorkspaceInviteConsumer {
    private final DeliveryPortalProperties props;

    public WorkspaceInviteConsumer(DeliveryPortalProperties props) {
        this.props = props;
    }

    public int consume(String email, long userId) {
        if (email == null || email.isBlank() || userId <= 0) {
            return 0;
        }
        try {
            return WorkspaceDirectory.open(Path.of(props.getStoreRoot()))
                    .consumePendingInvites(email, userId);
        } catch (Exception e) {
            return 0;
        }
    }
}
