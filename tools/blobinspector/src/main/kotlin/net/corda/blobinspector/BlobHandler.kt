package net.corda.blobinspector

class FileBlobHander(config_: Config) : BlobHandler(config_) {
    init {
        config_ as FileConfig
    }
}

abstract class BlobHandler (val config: Config){
    companion object {
        fun make(config: Config) : BlobHandler {
            return when (config.mode) {
                Mode.file -> FileBlobHander(config)
            }
        }
    }

}

