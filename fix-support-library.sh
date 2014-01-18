#!/bin/bash

# we must use the same support lib jar in all the dependencies
good_jar="app/libs/android-support-v4.jar"

# all these libs depend on android-support-v4.jar
readarray <<END
external/CacheWord/cachewordlib
external/OnionKit/libonionkit
END

for project in "${MAPFILE[@]}"; do
    project=${project%$'\n'} # remove trailing newline
    echo "updating $good_jar in $project"
    cp -f $good_jar $project/libs
done

# we must use the same support lib jar in all the dependencies
good_jar="app/libs/sqlcipher.jar"

# all these libs depend on android-support-v4.jar
readarray <<END
external/CacheWord/cachewordlib
END

for project in "${MAPFILE[@]}"; do
    project=${project%$'\n'} # remove trailing newline
    echo "updating $good_jar in $project"
    cp -f $good_jar $project/libs
done

# we must use the same support lib jar in all the dependencies
good_jar="app/libs/iocipher.jar"

# all these libs depend on android-support-v4.jar
readarray <<END
external/CacheWord/cachewordlib
END

for project in "${MAPFILE[@]}"; do
    project=${project%$'\n'} # remove trailing newline
    echo "updating $good_jar in $project"
    cp -f $good_jar $project/libs
done
