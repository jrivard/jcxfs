
# Path table

| Key           | Value        |
|---------------|--------------|
| [Full Path]   | [inode-uuid] |
| "/"           | 0            |
| "/file1"      | 1            |
| "/dir1"       | 2            |
| "/dir1/file2" | 3            |
| "/dir1/file3" | 5            |

# SubPath table (w/ duplicates)
| Key         | Value           |
|-------------|-----------------|
| [Full Path] | [sub-path name] |
| "/"         | "file1"         |
| "/"         | "dir1"          |
| "/dir1"     | "file2"         |
| "/dir1"     | "file3"         |


# inode table

| Key           | Value        |
|---------------|--------------|
| [idnode-uuid] | [entry-data] |
| 0             | {}           |
| 1             | {}           |
| 2             | {}           |
## inode table values
json encoded InodeEntry record

# data table

| Key                 | Value               |
|---------------------|---------------------|
| [inode-uuid]-[page] | [binary-file-data]  |
| 0-0                 | 00FF00FF00FF00FF... |
| 0-1                 | 00FF00FF00FF00FF... |
| 0-2                 | 00FF                |
| 1-1                 | 00FF00FF00FF00FF..  |

# data-length table

| Key          | Value        |
|--------------|--------------|
| [inode-uuid] | [length]     |
| 10           | 10           |
| 11           | 2321323      |
| 12           | 575476541232 |
| 13           | 42132194214  |


/path1/path2/path3