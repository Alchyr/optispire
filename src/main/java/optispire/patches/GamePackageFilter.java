package optispire.patches;

import org.clapper.util.classutil.ClassFilter;
import org.clapper.util.classutil.ClassFinder;
import org.clapper.util.classutil.ClassInfo;

import java.util.HashSet;
import java.util.Set;

public class GamePackageFilter implements ClassFilter {
    private static final Set<String> rejectedPackages;
    static {
        rejectedPackages = new HashSet<>();
        rejectedPackages.add("com.badlogic");
        rejectedPackages.add("com.esotericsoftware");
        rejectedPackages.add("com.fasterxml");
        rejectedPackages.add("com.gikk");
        rejectedPackages.add("com.google");
        rejectedPackages.add("com.jcraft");
        rejectedPackages.add("com.sun");
        rejectedPackages.add("de.robojumper");
        rejectedPackages.add("io.sentry");
        rejectedPackages.add("javazoom.jl");
        rejectedPackages.add("net.arikia");
        rejectedPackages.add("net.java");
        rejectedPackages.add("org.apache");
        rejectedPackages.add("org.lwjgl");
        rejectedPackages.add("org.slf4j");
    }

    public boolean accept(ClassInfo classInfo, ClassFinder classFinder) {
        String name = classInfo.getClassName();
        int secondPackage = name.indexOf('.');
        if (secondPackage >= 0)
        {
            secondPackage = name.indexOf('.', secondPackage + 1);

            if (secondPackage > 0)
            {
                name = name.substring(0, secondPackage);

                return !rejectedPackages.contains(name);
            }
        }

        return true;
    }
}
