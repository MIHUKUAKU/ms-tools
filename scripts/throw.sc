// Drop every item carried by an online fake player.
// Separate from ms.sc so an optional feature cannot break the core /ms app.

__config() -> {
    'scope' -> 'global',
    'stay_loaded' -> true,
    'command_permission' -> 'players',
    'commands' -> {
        '' -> '_help',
        '<online_fake>' -> 'throw_all'
    },
    'arguments' -> {
        'online_fake' -> {
            'type' -> 'term',
            'suggester' -> _(args) -> map(
                filter(player('all'), _~'player_type' == 'fake'),
                _~'name'
            )
        }
    }
};

_help() -> (
    print('THROW: /throw <online_fake>');
    print('Drops the fake player\'s complete inventory: main inventory, armor and offhand.');
    null
);

_online_fake(bot) -> (
    bot = str(bot);
    fake = player(bot);
    if(fake == null,
        (
            print('MS: player '+bot+' is not online.');
            null
        ),
        if(fake~'player_type' != 'fake',
            (
                print('MS: '+bot+' is online but is not a fake player.');
                null
            ),
            fake
        )
    )
);

throw_all(bot) -> (
    fake = _online_fake(bot);
    if(fake != null,
        (
            dropped_items = 0;
            dropped_stacks = 0;
            // A player inventory has 41 slots: 36 main, 4 armor and offhand.
            for(range(inventory_size(fake)),
                (
                    slot = _;
                    stack = inventory_get(fake, slot);
                    if(stack != null,
                        (
                            amount = drop_item(fake, slot);
                            if(amount > 0,
                                (
                                    dropped_items += amount;
                                    dropped_stacks += 1
                                )
                            )
                        )
                    )
                )
            );
            if(dropped_items == 0,
                print('MS: '+fake~'name'+' has no items to throw.'),
                print('MS: '+fake~'name'+' threw '+dropped_items+' item(s) in '+dropped_stacks+' stack(s).')
            )
        )
    );
    null
);
