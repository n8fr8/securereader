## Dev Setup

Follow these steps to setup your dev environment:

1. Checkout bigbuffalo git repo
2. Init and update git submodules

    git submodule update --init --recursive

3. Fix support library mismatch

    ActionBarSherlock uses an outdated version of the support library. We must use the same version
    of the library in BigBuffalo and ABS.

    **Command Line**

        ./fix-support-library.sh

    **Manually**

    Copy `app/libs/android-support-v4.jar` to `external/HoloEverywhere/contrib/ActionBarSherlock/actionbarsherlock/libs/android-support-v4.jar` and to `external/CacheWord/cachewordlib/libs/android-support-v4.jar`

4. Build Project

   **Using Eclipse**

    I recommend using a new workspace in Eclipse. I recommend using the root of
    this repo.

    Import into Eclipse (using the *"Existing Android Code Into Workspace"* option) the
    projects in the following order. Do not check "Copy projects into workspace".

    Note: The import order is crucial!

        app/
	external/CacheWord/cachewordlib
        external/HoloEverywhere/contrib/ActionBarSherlock/actionbarsherlock
        external/HoloEverywhere/library
        external/OnionKit/libonionkit

    When importing app/ double click on the value "MainActivity" and change it
    to "Big Buffalo" under the New Project Name heading before finishing the
    import.


   **Using command line**

        ./setup-ant.sh
        cd app/
        ant clean debug

### Troubleshooting

**Eclipse complains about overlapping an existing project when importing**

1. Make sure the project isn't in your workspace, if it is delete it (right click -> delete)
2. Close eclipse completely
2. Open the directory you're importing and delete `.project`, `.settings/`, `.classpath`
3. Restart eclipse, and import the project as an existing Android project

(sometimes an additional open/restart cycle is required to clear Eclipse's project cache)

**Invalid Project Description**

This is another occurrence of the previous problem, see above.
