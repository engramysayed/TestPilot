package delivery.net;

/**
 * Enforces {@link TargetNetworkPolicy} before browser navigation, redirects, and subresource loads.
 */
public final class WorkerNetworkGuard {
    private final TargetNetworkPolicy policy;

    public WorkerNetworkGuard(TargetNetworkPolicy policy) {
        this.policy = policy == null ? TargetNetworkPolicy.shared("") : policy;
    }

    public static WorkerNetworkGuard shared(String approvedOrigin) {
        return new WorkerNetworkGuard(TargetNetworkPolicy.shared(approvedOrigin));
    }

    public static WorkerNetworkGuard dedicated(String approvedOrigin, java.util.List<String> extraCidrs) {
        return new WorkerNetworkGuard(TargetNetworkPolicy.dedicated(approvedOrigin, extraCidrs));
    }

    public TargetNetworkPolicy policy() {
        return policy;
    }

    public void requireNavigate(String url) {
        TargetNetworkPolicy.Decision d = policy.inspect(url);
        if (!d.allowed()) {
            throw new TargetBlockedException(d.reason(), url);
        }
    }

    public void requireRedirect(String location, String fromUrl) {
        TargetNetworkPolicy.Decision d = policy.inspectRedirect(location, fromUrl);
        if (!d.allowed()) {
            throw new TargetBlockedException(d.reason(), location);
        }
    }

    public void requireSubresource(String url) {
        TargetNetworkPolicy.Decision d = policy.inspectSubresource(url);
        if (!d.allowed()) {
            throw new TargetBlockedException(d.reason(), url);
        }
    }

    public boolean credentialsAllowedAt(String currentUrl) {
        return policy.credentialsAllowedAt(currentUrl);
    }
}
