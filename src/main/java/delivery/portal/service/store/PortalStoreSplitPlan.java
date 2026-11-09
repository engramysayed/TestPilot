package delivery.portal.service.store;

/**
 * Audit C4 first pass — design target for splitting {@code PortalStore} (1700 lines, 92 public
 * methods, 105 callers) by concern. This class is intentionally empty; it exists only as a
 * stable Javadoc anchor so the plan survives across sessions and branches.
 *
 * <h2>Target shape</h2>
 * <pre>
 *   PortalStore (facade — keeps 92 public methods, each a 1-line delegate)
 *   ├── ProjectAccessStore     — getOwnedProject, canOperate, canAdminister,
 *   │                            projectMemberIds, listProjects, workspaceRole
 *   ├── JobLifecycleStore      — saveJob, getJob, getOwnedJob, beginWork,
 *   │                            syncJobPersistence, deleteOwnedJobRecord,
 *   │                            listJobEntities, reconcileExpiredLeases,
 *   │                            requestCancel
 *   ├── WorkspaceAccessStore   — tenantForProject, listProjectsForScheduleDispatch,
 *   │                            listProjectsForScheduleDispatchByTenant,
 *   │                            listWorkspaceRunners, enrollWorkspaceRunner
 *   ├── PromptOverrideStore    — preferredHooksJoined, prompt override CRUD
 *   └── ProjectArtifactStore   — projectDiskRoot, filesystemStoreFor, storeRoot,
 *                                archive / delete project folders
 * </pre>
 *
 * <h2>Migration order (per-branch, each a weekend)</h2>
 * <ol>
 *   <li><b>Phase 1 — split by concern, keep facade.</b> Create the five classes. Move each method
 *       body across. In PortalStore, replace the body with {@code return projectAccess.xxx(...)}.
 *       The 105 callers do not change. Full test suite must stay green between each class move.</li>
 *   <li><b>Phase 2 — migrate callers one controller at a time.</b> Each controller swaps
 *       {@code @Autowired PortalStore} for the specific store(s) it needs. Review size per PR.</li>
 *   <li><b>Phase 3 — delete PortalStore.</b> Once every caller is migrated, the facade is dead
 *       code.</li>
 * </ol>
 *
 * <h2>What this first pass ships</h2>
 * The plan above, as code-visible documentation, so later turns do not have to re-derive the
 * grouping. No existing code moves in this commit. Zero runtime effect.
 *
 * <h2>Risk register</h2>
 * <ul>
 *   <li>Several methods cross concerns (e.g. {@code beginWork} touches both the project row
 *       and the job entity). Keep them in {@link #PLACEHOLDER_JobLifecycleStore} and inject
 *       the project-access store if a membership check is needed.</li>
 *   <li>Some consumers use {@code @Autowired PortalStore} as a God-object spraying four
 *       concerns into one class. During Phase 2 keep the facade until no caller is left; then
 *       drop it, not before.</li>
 *   <li>Full test suite must pass between every move. See {@code docs/ops/AUDIT_PLAN.md} #10.</li>
 * </ul>
 */
public final class PortalStoreSplitPlan {
    private PortalStoreSplitPlan() {}

    /** Placeholder so Javadoc {@link} references above resolve when subclasses land. */
    static final String PLACEHOLDER_JobLifecycleStore = "see Javadoc";
}
