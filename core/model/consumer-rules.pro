# Consumer ProGuard/R8 rules for :core:model serialization classes.
-keepclassmembers enum com.projectmediapro.core.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
