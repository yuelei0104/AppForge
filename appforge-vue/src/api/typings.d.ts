declare namespace API {
  type AppAddRequest = {
    appName?: string
    cover?: string
    initPrompt?: string
    codeGenType?: string
    description?: string
    tags?: string[]
    visibility?: string
  }

  type AppAdminUpdateRequest = {
    id?: string | number
    appName?: string
    cover?: string
    priority?: number
    description?: string
    tags?: string[]
    visibility?: string
  }

  type AppDeployRequest = {
    appId?: string | number
  }

  type AppQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: string | number
    appName?: string
    cover?: string
    initPrompt?: string
    codeGenType?: string
    deployKey?: string
    priority?: number
    userId?: string | number
    searchText?: string
    tag?: string
    visibility?: string
    featureStatus?: string
    generationStatus?: string
  }

  type AppUpdateRequest = {
    id?: string | number
    appName?: string
    cover?: string
    description?: string
    tags?: string[]
    visibility?: string
  }

  type AppVersionVO = {
    version?: number
    kind?: string
    sourceVersion?: number
    summary?: string
    createTime?: string
  }

  type AppVO = {
    coverEnabled?: boolean
    coverMode?: string
    coverStatus?: string
    coverVersion?: number
    coverRevision?: string
    description?: string
    tags?: string[]
    visibility?: string
    generationStatus?: string
    generationError?: string
    currentVersion?: number
    deployedVersion?: number
    deploymentStatus?: string
    featureStatus?: string
    featureReason?: string
    id?: string | number
    appName?: string
    cover?: string
    initPrompt?: string
    codeGenType?: string
    deployKey?: string
    deployedTime?: string
    priority?: number
    userId?: string | number
    createTime?: string
    updateTime?: string
    user?: UserVO
  }

  type BaseResponseAppVO = {
    code?: number
    data?: AppVO
    message?: string
  }

  type BaseResponseBoolean = {
    code?: number
    data?: boolean
    message?: string
  }

  type BaseResponseChatMemoryVO = {
    code?: number
    data?: ChatMemoryVO
    message?: string
  }

  type BaseResponseChatStatisticsVO = {
    code?: number
    data?: ChatStatisticsVO
    message?: string
  }

  type BaseResponseInteger = {
    code?: number
    data?: number
    message?: string
  }

  type BaseResponseListAppVersionVO = {
    code?: number
    data?: AppVersionVO[]
    message?: string
  }

  type BaseResponseLoginUserVO = {
    code?: number
    data?: LoginUserVO
    message?: string
  }

  type BaseResponseLong = {
    code?: number
    data?: number
    message?: string
  }

  type BaseResponseMapStringString = {
    code?: number
    data?: Record<string, string>
    message?: string
  }

  type BaseResponsePageAppVO = {
    code?: number
    data?: PageAppVO
    message?: string
  }

  type BaseResponsePageChatHistory = {
    code?: number
    data?: PageChatHistory
    message?: string
  }

  type BaseResponsePageUserVO = {
    code?: number
    data?: PageUserVO
    message?: string
  }

  type BaseResponseString = {
    code?: number
    data?: string
    message?: string
  }

  type BaseResponseUser = {
    code?: number
    data?: User
    message?: string
  }

  type BaseResponseUserVO = {
    code?: number
    data?: UserVO
    message?: string
  }

  type ChatHistory = {
    id?: string | number
    message?: string
    messageType?: string
    appId?: string | number
    userId?: string | number
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type ChatHistoryQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: string | number
    message?: string
    messageType?: string
    appId?: string | number
    userId?: string | number
    lastCreateTime?: string
  }

  type ChatMemoryVO = {
    enabled?: boolean
    running?: boolean
    triggerMessages?: number
    recentMessages?: number
    coveredMessages?: number
    summary?: string
    lastError?: string
    updatedTime?: string
  }

  type ChatStatisticsVO = {
    roundCount?: number
    messageCount?: number
    aiMessageCount?: number
    lastMessageTime?: string
  }

  type chatToGenCodeParams = {
    appId: string | number
    message: string
  }

  type compressParams = {
    appId: string | number
  }

  type DeleteRequest = {
    id?: string | number
  }

  type deployAssetParams = {
    deployKey: string
    asset: string
  }

  type downloadSourceParams = {
    appId: string | number
  }

  type exportUsingGETParams = {
    appId: string | number
  }

  type filesParams = {
    appId: string | number
    version: number
  }

  type getAppVOByIdByAdminParams = {
    id: string | number
  }

  type getAppVOByIdParams = {
    id: string | number
  }

  type getDeployIndex1Params = {
    deployKey: string
  }

  type getDeployIndexParams = {
    deployKey: string
  }

  type getDeployResourceParams = {
    deployKey: string
    fileName: string
  }

  type getInfoParams = {
    id: string | number
  }

  type getPreviewIndex1Params = {
    appId: string | number
  }

  type getPreviewIndexParams = {
    appId: string | number
  }

  type getPreviewResourceParams = {
    appId: string | number
    fileName: string
  }

  type getUserByIdParams = {
    id: string | number
  }

  type getUserVOByIdParams = {
    id: string | number
  }

  type listAppChatHistoryParams = {
    appId: string | number
    pageSize?: number
    lastCreateTime?: string
    lastId?: string | number
  }

  type LoginUserVO = {
    id?: string | number
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
    updateTime?: string
  }

  type memoryParams = {
    appId: string | number
  }

  type PageAppVO = {
    records?: AppVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PageChatHistory = {
    records?: ChatHistory[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type pageParams = {
    page: PageChatHistory
  }

  type PageUserVO = {
    records?: UserVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type previewAssetParams = {
    appId: string | number
    asset: string
  }

  type removeParams = {
    id: string | number
  }

  type SseEmitter = {
    timeout?: number
  }

  type statisticsParams = {
    appId: string | number
  }

  type StreamingResponseBody = true

  type User = {
    id?: string | number
    userAccount?: string
    userPassword?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    editTime?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type UserAddRequest = {
    userName?: string
    userAccount?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserLoginRequest = {
    userAccount?: string
    userPassword?: string
  }

  type UserQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: string | number
    userName?: string
    userAccount?: string
    userProfile?: string
    userRole?: string
  }

  type UserRegisterRequest = {
    userAccount?: string
    userPassword?: string
    checkPassword?: string
  }

  type UserUpdateRequest = {
    id?: string | number
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserVO = {
    id?: string | number
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
  }

  type versionsParams = {
    appId: string | number
  }

  type WorkspaceRequest = {
    appId?: string | number
    version?: number
    approved?: boolean
    reason?: string
  }
}
