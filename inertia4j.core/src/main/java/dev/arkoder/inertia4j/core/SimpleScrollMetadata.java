package dev.arkoder.inertia4j.core;

class SimpleScrollMetadata implements ScrollMetadata {
    private final String pageName;
    private final Object previousPage;
    private final Object nextPage;
    private final Object currentPage;

    SimpleScrollMetadata(String pageName, Object previousPage, Object nextPage, Object currentPage) {
        this.pageName = pageName;
        this.previousPage = previousPage;
        this.nextPage = nextPage;
        this.currentPage = currentPage;
    }

    @Override
    public String getPageName() {
        return pageName;
    }

    @Override
    public Object getPreviousPage() {
        return previousPage;
    }

    @Override
    public Object getNextPage() {
        return nextPage;
    }

    @Override
    public Object getCurrentPage() {
        return currentPage;
    }
}
