# jcxfs
Encrypted FUSE file system.  Jcxfs is a mountable [FUSE](https://en.wikipedia.org/wiki/Filesystem_in_Userspace) filesystem 
that stores all data contents in an append-only log structured embedded file database.  This data format makes it ideal for 
periodic uploading/synchronization to remote data archive systems.  

## Data Storage
The data storage directory will look something like this example:

```
.r--r--r-- user user 8.0 MB 00000000800.xd
.r--r--r-- user user 8.0 MB 00000000g00.xd
.r--r--r-- user user 8.0 MB 00000000o00.xd
.r--r--r-- user user 8.0 MB 00000001000.xd
.r--r--r-- user user 8.0 MB 00000001800.xd
.r--r--r-- user user 8.0 MB 00000001g00.xd
.r--r--r-- user user 8.0 MB 00000001o00.xd
.r--r--r-- user user 8.0 MB 00000002000.xd
.r--r--r-- user user 8.0 MB 00000002800.xd
.r--r--r-- user user 3.2 MB 00000002g00.xd
.rw-r--r-- user user 277 B  jcxfs.env
.rw-r--r-- user user 2.0 KB xd.lck
```
File system data is stored in .xd files.  Filenames increment and are sortable so that the newest files are always last.
Existing files are never modified, only appended to.  If data is removed, the newer files are appened to indicate the removal.
Older files that are obsoleted will be periodically removed by a garbage collection process.  This does result in some 
file-space overhead.  By default jcxfs will use a file utilization level of 90%.  The utilization level value is trade
off between space overhead and performance of the database.  The database implementation used is 
[JetBrains Xodus](https://github.com/JetBrains/xodus)

The jcxfs.env contains environment settings required to open the database and cannot be removed or modified.

The xd.lck file ensures only one process can access the database at a time.

## Encryption
By default, jcxfs uses ChaCha20 for database encryption.  All database contents are encrypted, including pathnames and
metadata.  The database log files are a consistent size regardless of the fuse filesystem file contents, so information
about file sizes, directory structure or metadata is opaque.

Argon2 is used to derive a key from a user supplied password.

## Build

Build pre-requisites:
* Java 22+
* Git
* The build uses maven, but you do not need to install it; the maven wrapper in the source tree will download a local version.

Build steps:
1. Set _JAVA_HOME_ environment variable to JDK home.
1. Clone the git project
1. Change to jcxfs directory
1. Run the maven build

Linux example:
```
export JAVA_HOME="/home/vm/JavaJDKDirectory"
git clone https://github.com/jrivard/jcxfs
cd jcxfs
./mvnw clean verify
```  
Windows example:
```
set JAVA_HOME="c:\JavaJDKDirectory" 
git clone https://github.com/jrivard/jcxfs
cd jcxfs
mvnw.cmd clean verify
```
On Windows we recommend using paths without spaces for both jcxfs and JDK directory.

The output build file will be in ```target/jcxfs-0.0.0-executable.jar```

## Execute

Execute pre-requisites:
* Java 22 or later
* FUSE3 libraries installed 
  * On ubuntu use ```sudo apt install fuse```
* FUSE3 modules loaded
  * On ubuntu use ```sudo modprobe fuse```

Java command:
```java --enable-native-access=ALL-UNNAMED -jar jcxfs-0.0.0-executable.jar```

Java arguments:
The ```-Xmx1g``` argument imposes a 1GB memory limit for the jcxfs java process.  Most of this heap will be used for database
caching.

```--enable-native-access=ALL-UNNAMED``` argument is required for the java process to interact with the native fuse API on the operating system.

## Usage


### Init database

Prior to usage, the jcxfs database must be initialized.  This step insures the 
target directory is empty and gives an opportunity to specify database environment properties.  

Example:

```
java --enable-native-access=ALL-UNNAMED -jar jcxfs-0.0.0-executable.jar init -w "password" "/home/user/jcxfs-database"  

```

### Mount filesystem
To mount an initialized jcxfs database, use a command similar to the following:
```
java --enable-native-access=ALL-UNNAMED -jar jcxfs-0.0.0-executable.jar mount -w "password" "/home/user/jcxfs-database" /mnt/mountpoint
```
This will mount the jcxfs database at mount point `/mnt/mountpoint`.  If the mount is successful, the program
output will pause and wait for the user to press enter to exit.  Until the program exits, the file system
will remain mounted and can be accessed in another terminal or OS window.
