package org.ilay;

import org.ilay.api.Reverter;

/**
 * a reverter that will throw an exception if revert() is
 * attempted to be called more than once
 * @author Bernd Hopp bernd@vaadin.com
 */
abstract class ReverterBase implements Reverter {

    private boolean used = false;

    @Override
    public void revert() {
        Check.state(!used, "revert() can only be called once");

        revertInternal();

        used = true;
    }

    abstract void revertInternal();
}